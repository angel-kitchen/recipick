package com.recipick.domain.payment.facade;

import com.recipick.domain.member.service.MembershipBillingService;
import com.recipick.domain.payment.entity.MembershipPaymentAttempt;
import com.recipick.domain.payment.entity.MembershipRefund;
import com.recipick.domain.payment.entity.enums.MembershipOpsReviewReason;
import com.recipick.domain.payment.entity.enums.MembershipRefundOpsReason;
import com.recipick.domain.payment.entity.enums.MembershipRefundStatus;
import com.recipick.domain.payment.repository.MembershipPaymentAttemptRepository;
import com.recipick.domain.payment.repository.MembershipRefundRepository;
import com.recipick.domain.payment.service.MembershipRefundTicket;
import com.recipick.infra.portone.PortOneProperties;
import com.recipick.infra.portone.dto.PortOneMembershipCancelResult;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

// 환불 실행권과 토큰이 지키는 짧은 TX들. 환불 행 PK 한 행만 잠근다 — 회원·이용권·회차는 잠그지 않는다(결제 시도 선점 TX와 같은 원칙).
// 상태(REQUESTED)는 바꾸지 않고 토큰·임대·고정 요청값·최초 발송 시각·취소 ID·다음 확인 시각·운영 표시만 바꾼다.
// 토큰이 다르거나 임대가 끝났으면 아무것도 바꾸지 않고 false — 오래된 실행자는 새 실행자가 토큰을 올리기 전(임대 만료 뒤 재확보 전)에도
// 예약·메타데이터를 덮어쓰지 못한다.
// 확정 결과(COMPLETED·FAILED·강한 차단)는 여기서 쓰지 않는다 — 결과 반영 TX(MembershipRefundResultProcessor)의 몫
@Slf4j
@Component
@RequiredArgsConstructor
public class MembershipRefundExecutionProcessor {

    public static final Duration LEASE = Duration.ofMinutes(5);

    private final MembershipRefundRepository refunds;
    private final MembershipPaymentAttemptRepository attempts;
    private final MembershipBillingService membershipBillingService;
    private final PortOneProperties portOneProperties;
    private final Clock clock;

    // halted: 이번 실행권 TX에서 운영 확인 표시(설정 오류·발송 이력 불명)를 남겨 자동 실행을 멈췄다
    public record Claim(Optional<MembershipRefundTicket> ticket, boolean halted) {
        static Claim none() {
            return new Claim(Optional.empty(), false);
        }

        static Claim haltedNow() {
            return new Claim(Optional.empty(), true);
        }
    }

    // 실행권 확보. REQUESTED·운영 표시 없음·임대 없음/만료·회차 강한 차단 없음·다음 확인 시각 도래(없으면 즉시)일 때 토큰을 올리고
    // 임대 5분을 건다. 예약된 다음 확인 시각 전에는 잡지 않는다 — 접수 직후 큐 Worker와 동기 실행이 겹쳐도 예약보다 일찍 GET·재POST하지 않는다.
    // 같은 TX에서 아직 비어 있는 상점 ID·요청 사유를 고정한다(설정이 바뀌어도 기존 값 유지). 서버 상점 설정이 비어 있으면 PG 호출 없이
    // PG_CONFIG_ERROR 운영 확인으로 남긴다 — 응답의 상점이 다른 것(증거 불일치)과 구분한다
    @Transactional
    public Claim claim(Long refundId) {
        MembershipRefund refund = refunds.findByIdForUpdate(refundId).orElse(null);
        if (refund == null) {
            return Claim.none();
        }
        MembershipPaymentAttempt attempt = refund.getMembershipPaymentAttempt();
        if (roundBlocked(attempt)) {
            log.warn("강한 정합성 차단 회차의 환불은 실행권을 잡지 않음(운영 확인 대상): refundId={}", refundId);
            return Claim.none();
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (refund.getNextCheckAt() != null && now.isBefore(refund.getNextCheckAt())) {
            return Claim.none();
        }
        if (!refund.claim(now, LEASE)) {
            return Claim.none();
        }
        if (refund.getPgIdempotencyKey() == null) {
            // 실행 기능이 없던 때의 행. 발송 이력을 증명할 수 없어 자동 POST 금지 — 운영이 확인한 행만 별도 절차로 키를 채운다
            refund.requireOpsReview(MembershipRefundOpsReason.LEGACY_DISPATCH_UNPROVEN, now);
            refund.releaseLease();
            log.error("PG 키 없는 기존 환불 행 → 자동 실행 중단(운영 확인): refundId={}", refundId);
            return Claim.haltedNow();
        }
        if (refund.getPgStoreId() == null) {
            String storeId = portOneProperties.storeId();
            if (!StringUtils.hasText(storeId)) {
                refund.requireOpsReview(MembershipRefundOpsReason.PG_CONFIG_ERROR, now);
                refund.releaseLease();
                log.error("PortOne 상점 설정이 비어 있어 환불을 실행할 수 없음(운영 확인): refundId={}", refundId);
                return Claim.haltedNow();
            }
            refund.fixDispatchRequest(storeId);
        } else if (refund.getPgRequestReason() == null) {
            refund.fixDispatchRequest(refund.getPgStoreId());
        }
        Long memberId = membershipBillingService.findMemberIdOfMembership(attempt.getMembershipPayment().getMembershipId());
        return new Claim(Optional.of(ticketOf(refund, memberId)), false);
    }

    // POST 직전 준비. 현재 토큰·임대·REQUESTED·운영 표시 없음·고정 요청값·취소 ID 없음을 다시 확인하고, 환불 행 PK를 잠근 뒤 같은 회차의
    // 강한 차단(실행권 확보 뒤 다른 시도에서 생겼을 수 있다)과 재POST 창(최초 발송 + 2시간 55분)을 다시 본다 — 회차는 일반 조회만 하고
    // 잠그지 않는다(역순 잠금 금지). 최초 발송 시각이 없으면 지금 기록한다(있으면 그대로). 키와 사유는 매번 만들지 않는다.
    // 조건이 하나라도 깨지면 비어 있는 결과 — 호출자는 POST를 보내지 않는다. PG 호출이 TX 밖이라 경합을 완전히 없애지는 못하므로
    // 최종 결과 반영(TX2)의 회차 차단 검사는 그대로 둔다
    @Transactional
    public Optional<MembershipRefundTicket> prepareFirstDispatch(MembershipRefundTicket ticket) {
        MembershipRefund refund = refunds.findByIdForUpdate(ticket.refundId()).orElse(null);
        LocalDateTime now = LocalDateTime.now(clock);
        if (refund == null || !refund.holdsClaim(ticket.claimToken(), now)
            || refund.getStatus() != MembershipRefundStatus.REQUESTED || refund.getOpsReviewReason() != null
            || !refund.hasFixedDispatchRequest() || refund.getPortoneCancellationId() != null) {
            return Optional.empty();
        }
        if (roundBlocked(refund.getMembershipPaymentAttempt())) {
            log.warn("POST 직전 같은 회차에 강한 정합성 차단이 생겨 취소를 보내지 않음(운영 확인 대상): refundId={}", ticket.refundId());
            return Optional.empty();
        }
        if (refund.getFirstDispatchedAt() != null
            && !now.isBefore(refund.getFirstDispatchedAt().plus(MembershipRefundExecutionFacade.REPOST_WINDOW))) {
            log.info("POST 직전 같은 키 재POST 창이 끝나 보내지 않음(GET 정합화만 계속): refundId={}", ticket.refundId());
            return Optional.empty();
        }
        refund.markFirstDispatched(now);
        return Optional.of(ticket.withFirstDispatchedAt(refund.getFirstDispatchedAt()));
    }

    // POST 응답의 취소 ID 기록. 티켓·토큰·REQUESTED·취소 ID 존재·응답 금액 = 저장 환불액·응답 reason = 저장 요청 사유·기존 ID 동일·
    // 다른 환불이 그 ID를 쓰지 않음을 모두 확인한 뒤에만 기록한다. 기록하지 못해도 이미 발생한 결과는 버리지 않는다 —
    // 같은 키·reason의 GET 정합화가 이어 간다. 최종 COMPLETED·FAILED는 여기서 쓰지 않는다
    @Transactional
    public boolean recordCancellationReceipt(MembershipRefundTicket ticket, PortOneMembershipCancelResult receipt) {
        if (receipt == null || !receipt.hasCancellationId()) {
            return false;
        }
        MembershipRefund refund = refunds.findByIdForUpdate(ticket.refundId()).orElse(null);
        LocalDateTime now = LocalDateTime.now(clock);
        if (refund == null || !refund.getMembershipPaymentAttempt().getId().equals(ticket.attemptId())
            || !refund.holdsClaim(ticket.claimToken(), now) || refund.getStatus() != MembershipRefundStatus.REQUESTED) {
            log.warn("취소 응답 ID를 기록하지 않음(토큰·상태 불일치, GET 정합화로 이어 감): refundId={}", ticket.refundId());
            return false;
        }
        if (receipt.totalAmount() == null || !receipt.totalAmount().equals(refund.getAmount())
            || receipt.reason() == null || !receipt.reason().equals(refund.getPgRequestReason())) {
            log.error("취소 응답의 금액·reason이 저장 근거와 다름 → 귀속하지 않음(재조회로 확인): refundId={}, amount={}",
                ticket.refundId(), receipt.totalAmount());
            return false;
        }
        if (refund.getPortoneCancellationId() != null
            && !refund.getPortoneCancellationId().equals(receipt.cancellationId())) {
            log.error("취소 응답 ID가 저장된 ID와 다름 → 귀속하지 않음(재조회로 확인): refundId={}", ticket.refundId());
            return false;
        }
        boolean usedByOther = refunds.findIdsByMembershipPaymentAttemptIdOrderByIdAsc(ticket.attemptId()).stream()
            .filter(id -> !id.equals(ticket.refundId()))
            .map(id -> refunds.findById(id).orElse(null))
            .anyMatch(r -> r != null && receipt.cancellationId().equals(r.getPortoneCancellationId()));
        if (usedByOther) {
            log.error("취소 응답 ID를 다른 환불이 이미 사용 → 귀속하지 않음(재조회로 확인): refundId={}", ticket.refundId());
            return false;
        }
        refund.recordCancellationId(receipt.cancellationId());
        return true;
    }

    // 다음 확인 예약(토큰 일치 + 임대 유효 때만). 임대는 끝낸다 — 다음 확인은 복구 스캔이 다시 실행권을 잡아 수행한다.
    // 임대가 끝난 실행자의 예약은 받지 않는다 — 그 행은 복구 스캔이 바로 다시 잡는다
    @Transactional
    public boolean scheduleNextCheck(MembershipRefundTicket ticket, LocalDateTime nextCheckAt) {
        MembershipRefund refund = refunds.findByIdForUpdate(ticket.refundId()).orElse(null);
        LocalDateTime now = LocalDateTime.now(clock);
        if (refund == null || !refund.holdsClaim(ticket.claimToken(), now)
            || refund.getStatus() != MembershipRefundStatus.REQUESTED) {
            return false;
        }
        refund.scheduleNextCheck(nextCheckAt);
        refund.releaseLease();
        return true;
    }

    // 자동 실행 중단 표시(토큰 일치 + 임대 유효 때만). REQUESTED는 유지하고 임대·예약을 끝낸다 — 관리자 재조회·검증된 웹훅으로만 움직인다.
    // 임대가 끝난 실행자의 판단은 반영하지 않는다 — 다음 실행권이 같은 관측을 다시 하고 판단한다
    @Transactional
    public boolean haltAuto(MembershipRefundTicket ticket, MembershipRefundOpsReason reason) {
        MembershipRefund refund = refunds.findByIdForUpdate(ticket.refundId()).orElse(null);
        LocalDateTime now = LocalDateTime.now(clock);
        if (refund == null || !refund.holdsClaim(ticket.claimToken(), now)
            || refund.getStatus() != MembershipRefundStatus.REQUESTED) {
            return false;
        }
        refund.requireOpsReview(reason, now);
        refund.scheduleNextCheck(null);
        refund.releaseLease();
        log.warn("환불 자동 실행 중단(운영 확인): refundId={}, reason={}", ticket.refundId(), reason);
        return true;
    }

    // 실행권 해제(토큰 일치 + 임대 유효 때만). 확정·차단·POST 준비 거절 뒤 남은 임대를 정리한다. TX2가 확정하며 임대를 이미 지웠거나
    // 새 실행자가 토큰을 올렸으면 아무것도 하지 않는다 — 이전 실행자가 새 실행권을 해제하지 못한다
    @Transactional
    public void releaseLease(MembershipRefundTicket ticket) {
        MembershipRefund refund = refunds.findByIdForUpdate(ticket.refundId()).orElse(null);
        if (refund != null && refund.holdsClaim(ticket.claimToken(), LocalDateTime.now(clock))) {
            refund.releaseLease();
        }
    }

    // 회차에 강한 정합성 차단 시도가 있는지(일반 조회, 잠금 없음)
    private boolean roundBlocked(MembershipPaymentAttempt attempt) {
        return attempts.existsByMembershipPaymentIdAndOpsReviewReasonIn(attempt.getMembershipPayment().getId(),
            MembershipOpsReviewReason.BLOCKING);
    }

    private static MembershipRefundTicket ticketOf(MembershipRefund refund, Long memberId) {
        MembershipPaymentAttempt attempt = refund.getMembershipPaymentAttempt();
        return new MembershipRefundTicket(refund.getId(), attempt.getId(), attempt.getMembershipPayment().getId(),
            attempt.getMembershipPayment().getMembershipId(), memberId, attempt.getPortonePaymentId(),
            refund.getClaimToken(), refund.getLeaseExpiresAt(), refund.getPgIdempotencyKey(), refund.getPgStoreId(),
            refund.getPgRequestReason(), refund.getAmount(),
            refund.getPaidAmountSnapshot() != null ? refund.getPaidAmountSnapshot() : refund.getAmount(),
            refund.getRequestedBy(),
            refund.getRequestedAt(), refund.getFirstDispatchedAt(), refund.getPortoneCancellationId());
    }
}
