package com.recipick.domain.payment.facade;

import com.recipick.domain.member.dto.BillingMembershipData;
import com.recipick.domain.member.dto.MemberSettlementLock;
import com.recipick.domain.member.entity.enums.MembershipSource;
import com.recipick.domain.member.entity.enums.MembershipType;
import com.recipick.domain.member.service.MemberService;
import com.recipick.domain.member.service.MembershipBillingService;
import com.recipick.domain.payment.dto.response.MembershipRefundResult;
import com.recipick.domain.payment.entity.MembershipPayment;
import com.recipick.domain.payment.entity.MembershipPaymentAttempt;
import com.recipick.domain.payment.entity.MembershipRefund;
import com.recipick.domain.payment.entity.enums.MembershipOpsReviewReason;
import com.recipick.domain.payment.entity.enums.MembershipPaymentAttemptStatus;
import com.recipick.domain.payment.entity.enums.MembershipRefundStatus;
import com.recipick.domain.payment.entity.enums.RefundRequester;
import com.recipick.domain.payment.exception.PaymentErrorCode;
import com.recipick.domain.payment.repository.MembershipPaymentRepository;
import com.recipick.domain.payment.repository.MembershipRefundRepository;
import com.recipick.domain.payment.service.MembershipBillingPeriods;
import com.recipick.domain.payment.service.MembershipBillingPeriods.FirstPeriod;
import com.recipick.domain.payment.service.MembershipBillingPeriods.RenewalPeriod;
import com.recipick.domain.payment.service.MembershipPaymentQueryService;
import com.recipick.domain.payment.service.MembershipRefundAcceptResult;
import com.recipick.domain.payment.service.MembershipRefundAcceptResult.RejectReason;
import com.recipick.domain.payment.service.MembershipRefundCalculator;
import com.recipick.domain.payment.service.MembershipRefundCalculator.Calculation;
import com.recipick.domain.payment.service.MembershipRefundRef;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.exception.CommonErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 환불 접수 TX. 잠금 순서 MEMBER → 이용권 → 회차 PK → 시도 PK 오름차순 → 환불 PK 오름차순(ID만 읽고 한 행씩).
// 접수 판정에 쓰는 시각은 잠금을 모두 잡은 뒤 읽는다 — 잠금을 기다리는 사이 자정이나 이용기간 종료를 넘겼으면 그 시각으로 본다.
// 거절은 예외 대신 결과로 돌려준다 — 차단 표시를 남긴 거절은 그 표시를 커밋해야 하고, Facade가 커밋 뒤 BusinessException으로 바꾼다.
// 같은 키의 재요청은 자격 검사보다 먼저 기존 결과를 돌려준다(새 계산·새 행·PG 호출 없음). 환불액·기간은 여기서 한 번만 계산해 저장한다.
// 0원 환불은 PG 없이 같은 TX에서 완료와 이용권 종료까지 끝낸다. Entity는 TX 밖으로 내보내지 않는다
@Slf4j
@Component
@RequiredArgsConstructor
public class MembershipRefundRequestProcessor {

    private static final int REFUND_ZERO = 0;

    private final MemberService memberService;
    private final MembershipBillingService membershipBillingService;
    private final MembershipPaymentQueryService membershipPaymentQueryService;
    private final MembershipPaymentRepository payments;
    private final MembershipRefundRepository refunds;
    private final MembershipRenewalRules rules;
    private final Clock clock;

    // 즉시 해지 접수. storedKey = "mrf_member:{memberId}:{정규화 키}", requestHash = 대상 이용권 ID의 해시
    @Transactional
    public MembershipRefundAcceptResult acceptMember(Long memberId, Long membershipId, String storedKey,
                                                     String requestHash) {
        memberService.lockActiveMember(memberId);
        Optional<MembershipRefund> existing = refunds.findByIdempotencyKey(storedKey);
        if (existing.isPresent()) {
            MembershipRefund found = existing.get();
            if (!requestHash.equals(found.getRequestHash())) {
                throw new BusinessException(CommonErrorCode.IDEMPOTENCY_KEY_PAYLOAD_MISMATCH);
            }
            return MembershipRefundAcceptResult.existing(found.getId());
        }
        BillingMembershipData membership = membershipBillingService.getBillingMembershipForUpdate(memberId, membershipId);
        if (!terminableState(membership)) {
            return MembershipRefundAcceptResult.rejected(RejectReason.NOT_TERMINABLE);
        }
        if (membershipPaymentQueryService.existsUnresolved(membershipBillingService.findMembershipIds(memberId))) {
            return MembershipRefundAcceptResult.rejected(RejectReason.PAYMENT_IN_PROGRESS);
        }
        List<Long> candidateIds = payments.findLatestAppliedPaidIds(membershipId, PageRequest.of(0, 1));
        if (candidateIds.isEmpty()) {
            log.error("즉시 해지 접수: 정상 반영된 PAID 회차가 없음(표시할 행 없음) → 거절: membershipId={}", membershipId);
            return MembershipRefundAcceptResult.rejected(RejectReason.SETTLEMENT_BLOCKED);
        }
        MembershipPayment payment = payments.findByIdForUpdate(candidateIds.get(0))
            .orElseThrow(() -> new BusinessException(PaymentErrorCode.MEMBERSHIP_PAYMENT_NOT_FOUND));
        if (!payment.getMembershipId().equals(membershipId)) {
            throw new IllegalStateException("회차·이용권 연결이 맞지 않음: membershipPaymentId=" + payment.getId());
        }
        List<MembershipPaymentAttempt> attempts = rules.lockAttempts(payment);
        LocalDateTime now = LocalDateTime.now(clock);
        if (!eligibleForTermination(membership, now)) {
            return MembershipRefundAcceptResult.rejected(RejectReason.NOT_TERMINABLE);
        }
        if (attempts.stream().anyMatch(MembershipPaymentAttempt::isSettlementBlocked)) {
            log.warn("즉시 해지 접수: 강한 정합성 차단 회차 → 거절: membershipPaymentId={}", payment.getId());
            return MembershipRefundAcceptResult.rejected(RejectReason.SETTLEMENT_BLOCKED);
        }
        Period period = restorePeriod(payment);
        if (period == null || !period.endAt().equals(membership.endAt())) {
            log.error("즉시 해지 접수: 최신 반영 회차의 종료 시각이 이용권과 다름 → 차단: membershipPaymentId={}, membershipId={}",
                payment.getId(), membershipId);
            return blockTargetPeriodMismatch(attempts, now);
        }
        List<MembershipPaymentAttempt> applied = attempts.stream().filter(a -> appliedBy(payment, a)).toList();
        if (applied.size() != 1) {
            log.error("즉시 해지 접수: 정상 반영 PAID 시도가 정확히 하나가 아님({}) → 차단: membershipPaymentId={}", applied.size(),
                payment.getId());
            return blockTargetPeriodMismatch(attempts, now);
        }
        MembershipPaymentAttempt attempt = applied.get(0);
        List<MembershipRefund> rows = lockRefunds(attempt.getId());
        if (rows.stream().anyMatch(MembershipRefund::isSettlementBlocked)) {
            return MembershipRefundAcceptResult.rejected(RejectReason.SETTLEMENT_BLOCKED);
        }
        if (rows.stream().anyMatch(r -> r.getStatus() == MembershipRefundStatus.REQUESTED)) {
            return MembershipRefundAcceptResult.rejected(RejectReason.PAYMENT_IN_PROGRESS);
        }
        if (rows.stream().anyMatch(r -> r.getStatus() == MembershipRefundStatus.COMPLETED)) {
            return MembershipRefundAcceptResult.rejected(RejectReason.NOT_TERMINABLE);
        }
        Calculation calculation = MembershipRefundCalculator.calculate(payment.getAmount(), period.startAt(),
            period.endAt(), now);
        MembershipRefund refund = refunds.save(MembershipRefund.requestedByMember(payment, attempt, calculation,
            storedKey, requestHash, period.startAt(), period.endAt(), now));
        if (calculation.refundAmount() == REFUND_ZERO) {
            refund.markCompleted(now, null, null);
            membershipBillingService.terminateAfterRefund(membershipId, now);
            log.info("즉시 해지 0원 환불 완료·이용권 종료: refundId={}, membershipId={}", refund.getId(), membershipId);
            return MembershipRefundAcceptResult.completedWithoutPg(refund.getId());
        }
        log.info("즉시 해지 환불 접수: refundId={}, membershipId={}, attemptId={}, amount={}", refund.getId(), membershipId,
            attempt.getId(), calculation.refundAmount());
        return MembershipRefundAcceptResult.accepted(refund.getId());
    }

    // SYSTEM FAILED 환불의 관리자 재실행 접수. 회원 ID는 호출자가 TX 밖에서 역조회해 넘기고, 이 TX의 첫 DB 작업은 회원 잠금이다
    // (잠금 전에 일반 조회가 있으면 그 스냅숏으로 뒤의 환불 목록 조회가 고정돼 다른 요청이 막 넣은 후속 행을 놓치고 UNIQUE에만 기댄다).
    // 기존 후속 행이 있으면 그 행을 돌려주고(멱등), 없으면 원래 행·같은 시도의 다른 환불·차단·금액 이력을 검증한 뒤 새 후속 행을 저장한다
    @Transactional
    public MembershipRefundAcceptResult acceptSystemRetry(Long memberId, Long failedRefundId) {
        MemberSettlementLock member = memberService.lockMemberForSettlement(memberId);
        MembershipRefundRef ref = refunds.findRef(failedRefundId)
            .orElseThrow(() -> new BusinessException(PaymentErrorCode.MEMBERSHIP_REFUND_NOT_FOUND));
        membershipBillingService.getBillingMembershipForUpdate(member.memberId(), ref.membershipId());
        MembershipPayment payment = payments.findByIdForUpdate(ref.membershipPaymentId())
            .orElseThrow(() -> new BusinessException(PaymentErrorCode.MEMBERSHIP_PAYMENT_NOT_FOUND));
        List<MembershipPaymentAttempt> attempts = rules.lockAttempts(payment);
        MembershipPaymentAttempt attempt = attempts.stream().filter(a -> a.getId().equals(ref.attemptId())).findFirst()
            .orElseThrow(() -> new IllegalStateException("환불·시도 연결이 맞지 않음: refundId=" + failedRefundId));
        List<MembershipRefund> rows = lockRefunds(attempt.getId());
        MembershipRefund failed = rows.stream().filter(r -> r.getId().equals(failedRefundId)).findFirst()
            .orElseThrow(() -> new BusinessException(PaymentErrorCode.MEMBERSHIP_REFUND_NOT_FOUND));
        LocalDateTime now = LocalDateTime.now(clock);
        Optional<MembershipRefund> followUp = rows.stream()
            .filter(r -> failedRefundId.equals(r.getRetryOfRefundId())).findFirst();
        if (followUp.isPresent()) {
            return MembershipRefundAcceptResult.existing(followUp.get().getId());
        }
        if (failed.getRequestedBy() != RefundRequester.SYSTEM || failed.getStatus() != MembershipRefundStatus.FAILED
            || failed.isSettlementBlocked()
            || attempts.stream().anyMatch(MembershipPaymentAttempt::isSettlementBlocked)
            || rows.stream().anyMatch(MembershipRefund::isSettlementBlocked)
            || rows.stream().anyMatch(r -> r.getStatus() == MembershipRefundStatus.REQUESTED)
            || rows.stream().anyMatch(r -> r.getStatus() == MembershipRefundStatus.COMPLETED)) {
            return MembershipRefundAcceptResult.rejected(RejectReason.RETRY_NOT_ALLOWED);
        }
        List<MembershipRefund> systemRows = rows.stream().filter(r -> r.getRequestedBy() == RefundRequester.SYSTEM)
            .toList();
        if (systemRows.stream().anyMatch(r -> !r.getAmount().equals(failed.getAmount()))) {
            log.error("SYSTEM 재실행 접수: 같은 시도의 SYSTEM 환불 금액이 서로 다름 → 거절: attemptId={}", attempt.getId());
            return MembershipRefundAcceptResult.rejected(RejectReason.RETRY_NOT_ALLOWED);
        }
        MembershipRefund retry = refunds.save(MembershipRefund.systemRetryOf(failed, systemRows.size() + 1, now));
        log.info("SYSTEM 환불 재실행 접수: failedRefundId={}, retryRefundId={}, amount={}", failedRefundId, retry.getId(),
            retry.getAmount());
        return MembershipRefundAcceptResult.accepted(retry.getId());
    }

    // 회원 응답 조립(짧은 조회 TX). MEMBER 행만 — SYSTEM 행은 기간 값이 없어 이 응답으로 만들지 않는다
    @Transactional(readOnly = true)
    public MembershipRefundResult memberResult(Long refundId) {
        MembershipRefund refund = refunds.findById(refundId)
            .orElseThrow(() -> new BusinessException(PaymentErrorCode.MEMBERSHIP_REFUND_NOT_FOUND));
        return MembershipRefundResult.of(refund);
    }

    // ---------- 내부 ----------

    // 시각과 무관한 자격: 유료·구독/전환·미종료·활성화됨. 잠금 뒤 시각 검사(eligibleForTermination) 전에 명백한 부적격을 거른다
    private static boolean terminableState(BillingMembershipData m) {
        return m.type() == MembershipType.PAID
            && (m.source() == MembershipSource.SUBSCRIBE || m.source() == MembershipSource.TRIAL_CONVERT)
            && m.terminatedAt() == null
            && m.activatedAt() != null;
    }

    // 즉시 해지 자격(잠금 뒤 시각 기준): 위 상태 + 활성화 시각 ≤ now, 시작 시각 ≤ now < 혜택 종료(유예 끝 또는 종료)
    private static boolean eligibleForTermination(BillingMembershipData m, LocalDateTime now) {
        LocalDateTime effectiveEnd = m.graceEndAt() != null ? m.graceEndAt() : m.endAt();
        return terminableState(m)
            && !m.activatedAt().isAfter(now)
            && m.startAt() != null && !m.startAt().isAfter(now)
            && effectiveEnd != null && now.isBefore(effectiveEnd);
    }

    private record Period(LocalDateTime startAt, LocalDateTime endAt) {
    }

    // 회차에서 결제기간 [S, E)를 되살린다. 첫 결제: S = PG 결제 시각, E = 첫 기간 종료. 갱신: 저장된 회차 값에서 T·E.
    // 저장된 시작·종료일과 맞지 않으면 null(차단 대상)
    private static Period restorePeriod(MembershipPayment payment) {
        if (payment.getPaidAt() == null) {
            return null;
        }
        if (payment.isRenewal()) {
            try {
                RenewalPeriod stored = MembershipRenewalRules.storedPeriod(payment);
                return new Period(stored.previousEndAt(), stored.endAt());
            } catch (IllegalStateException e) {
                return null;
            }
        }
        FirstPeriod first = MembershipBillingPeriods.firstPeriod(payment.getPaidAt());
        if (!first.periodStart().equals(payment.getPeriodStartDate())
            || !first.periodEnd().equals(payment.getPeriodEndDate())) {
            return null;
        }
        return new Period(payment.getPaidAt(), first.endAt());
    }

    // 정상 반영 PAID 시도: PAID·환불 필요 없음·PG 결제 시각이 회차 결제 시각과 µs까지 같음
    private static boolean appliedBy(MembershipPayment payment, MembershipPaymentAttempt attempt) {
        return attempt.getStatus() == MembershipPaymentAttemptStatus.PAID
            && attempt.getRefundRequiredAt() == null
            && attempt.getPgPaidAt() != null && payment.getPaidAt() != null
            && attempt.getPgPaidAt().truncatedTo(ChronoUnit.MICROS)
            .equals(payment.getPaidAt().truncatedTo(ChronoUnit.MICROS));
    }

    // 회차의 PAID 시도들에 대상 불일치 차단을 남기고 거절한다. 표시할 PAID 시도가 없으면 ERROR 로그만 — 가짜 행을 만들지 않는다
    private static MembershipRefundAcceptResult blockTargetPeriodMismatch(List<MembershipPaymentAttempt> attempts,
                                                                          LocalDateTime now) {
        List<MembershipPaymentAttempt> paid = attempts.stream()
            .filter(a -> a.getStatus() == MembershipPaymentAttemptStatus.PAID).toList();
        if (paid.isEmpty()) {
            log.error("즉시 해지 접수: 차단 표시를 남길 PAID 시도가 없음 → 거절만");
        }
        paid.forEach(a -> a.blockSettlement(now, MembershipOpsReviewReason.REFUND_TARGET_PERIOD_MISMATCH));
        return MembershipRefundAcceptResult.rejected(RejectReason.SETTLEMENT_BLOCKED);
    }

    private List<MembershipRefund> lockRefunds(Long attemptId) {
        List<MembershipRefund> locked = new ArrayList<>();
        for (Long id : refunds.findIdsByMembershipPaymentAttemptIdOrderByIdAsc(attemptId)) {
            locked.add(refunds.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalStateException("잠그려던 환불 행이 사라짐: refundId=" + id)));
        }
        return locked;
    }
}
