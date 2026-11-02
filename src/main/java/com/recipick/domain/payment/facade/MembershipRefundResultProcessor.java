package com.recipick.domain.payment.facade;

import com.recipick.domain.member.dto.BillingMembershipData;
import com.recipick.domain.member.dto.MemberSettlementLock;
import com.recipick.domain.member.service.MemberService;
import com.recipick.domain.member.service.MembershipBillingService;
import com.recipick.domain.payment.entity.MembershipPayment;
import com.recipick.domain.payment.entity.MembershipPaymentAttempt;
import com.recipick.domain.payment.entity.MembershipRefund;
import com.recipick.domain.payment.entity.enums.MembershipOpsReviewReason;
import com.recipick.domain.payment.entity.enums.MembershipRefundOpsReason;
import com.recipick.domain.payment.entity.enums.MembershipRefundStatus;
import com.recipick.domain.payment.entity.enums.RefundRequester;
import com.recipick.domain.payment.exception.PaymentErrorCode;
import com.recipick.domain.payment.repository.MembershipPaymentAttemptRepository;
import com.recipick.domain.payment.repository.MembershipPaymentRepository;
import com.recipick.domain.payment.repository.MembershipRefundRepository;
import com.recipick.domain.payment.service.MembershipPaymentAttemptRef;
import com.recipick.domain.payment.service.MembershipRefundApplyResult;
import com.recipick.domain.payment.service.MembershipRefundApplyResult.Signal;
import com.recipick.domain.payment.service.MembershipRefundCancellationMatcher;
import com.recipick.domain.payment.service.MembershipRefundCancellationMatcher.CancelledSum;
import com.recipick.domain.payment.service.MembershipRefundCancellationMatcher.Identity;
import com.recipick.domain.payment.service.MembershipRefundCancellationMatcher.Match;
import com.recipick.domain.payment.service.MembershipRefundExecutionResult;
import com.recipick.domain.payment.service.RefundCancellationBinding;
import com.recipick.domain.payment.service.RefundMatchSnapshot;
import com.recipick.global.exception.BusinessException;
import com.recipick.infra.portone.PortOneProperties;
import com.recipick.infra.portone.dto.PortOneCancellation;
import com.recipick.infra.portone.dto.PortOneCancellationStatus;
import com.recipick.infra.portone.dto.PortOneCancellationTrigger;
import com.recipick.infra.portone.dto.PortOnePayment;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;

// 환불 결과 반영 TX(TX2). 잠금 순서 MEMBER(정산 잠금 — 탈퇴 회원 포함) → 이용권 → 회차 PK → 시도 PK 오름차순 → 환불 PK 오름차순.
// 회원 ID는 호출자가 트랜잭션 밖에서 역조회해 넘기고, 이 TX의 첫 DB 작업은 회원 잠금이다 — 잠금 전에 일반 조회가 있으면 그 스냅숏으로
// 뒤의 일반 조회(환불 ID 목록 등)가 고정돼 잠금 뒤 커밋된 행을 놓친다. 잠근 뒤 연결을 다시 검증한다.
// 토큰은 요구하지 않는다 — 웹훅·관리자 재조회는 실행자의 토큰을 갖지 않으므로, 확정 결과는 잠금 순서·현재 환불 상태·취소 ID·reason·금액·
// 대상 연결·기존 확정 근거와의 모순·회차 전체 강한 차단으로만 지킨다. 유효한 금전 결과를 "옛 토큰"이라는 이유로 버리지 않는다.
// 한 관측(GET 1회)으로 그 시도의 환불 행 전부를 ID 순서로 평가한다 — 확정 행은 재전송 모순 검사, REQUESTED 행은 식별·누적 취소액·
// 소유권 매칭 뒤 반영. MEMBER 성공은 완료와 이용권 종료를 같은 TX에서 커밋하고 SYSTEM 성공은 종료를 부르지 않는다.
// 결제 시도·회차의 상태 전이 메서드는 부르지 않는다(환불돼도 PAID 기록은 유지) — 차단 표시(blockSettlement)만 남긴다
@Slf4j
@Component
@RequiredArgsConstructor
public class MembershipRefundResultProcessor {

    public static final String CODE_PG_CANCEL_FAILED = "PG_CANCEL_FAILED";

    private final MemberService memberService;
    private final MembershipBillingService membershipBillingService;
    private final MembershipPaymentRepository payments;
    private final MembershipPaymentAttemptRepository attempts;
    private final MembershipRefundRepository refunds;
    private final MembershipRenewalRules rules;
    private final PortOneProperties portOneProperties;
    private final Clock clock;

    // memberId·attemptId는 호출자가 TX 밖에서 역조회한 값. focusRefundId가 있으면 그 환불 한 건의 결과, null이면 그 시도의 환불 전체를
    // 집계한 결과 — 환불 행이 없는 취소 관측은 외부 취소라 자동 귀속하지 않고 운영 확인(REVIEW_REQUIRED), 여러 행은
    // REVIEW_REQUIRED > PENDING > COMPLETED > FAILED 순으로 보수적으로 집계한다(옛 FAILED가 새 REQUESTED의 미확정을 가리지 않게)
    @Transactional
    public MembershipRefundApplyResult apply(Long memberId, Long attemptId, Long focusRefundId,
                                             PortOnePayment observed) {
        if (observed == null) {
            throw new IllegalArgumentException("PG 관측값이 필요합니다");
        }
        Locked locked = lock(memberId, attemptId);
        LocalDateTime now = LocalDateTime.now(clock);
        if (locked.attempts().stream().anyMatch(MembershipPaymentAttempt::isSettlementBlocked)) {
            log.warn("강한 정합성 차단 회차의 환불 결과 반영을 건너뜀(운영 확인 대상): attemptId={}, membershipPaymentId={}", attemptId,
                locked.payment().getId());
            return MembershipRefundApplyResult.of(MembershipRefundExecutionResult.REVIEW_REQUIRED);
        }
        if (focusRefundId == null && locked.refunds().isEmpty()) {
            log.error("환불 요청이 없는 수납 시도에 취소 관측(외부 취소, 운영 확인): attemptId={}, membershipPaymentId={}", attemptId,
                locked.payment().getId());
            return MembershipRefundApplyResult.of(MembershipRefundExecutionResult.REVIEW_REQUIRED);
        }
        MembershipRefundApplyResult focus = null;
        MembershipRefundExecutionResult aggregated = null;
        for (MembershipRefund refund : locked.refunds()) {
            MembershipRefundApplyResult result = evaluate(locked, refund, observed, now);
            if (refund.getId().equals(focusRefundId)) {
                focus = result;
            }
            aggregated = moreConservative(aggregated, result.outcome());
        }
        if (focusRefundId == null) {
            return MembershipRefundApplyResult.of(aggregated);
        }
        if (focus == null) {
            throw new BusinessException(PaymentErrorCode.MEMBERSHIP_REFUND_NOT_FOUND);
        }
        return focus;
    }

    // 집계 우선순위 REVIEW_REQUIRED > PENDING > COMPLETED > FAILED. SKIPPED는 TX2에서 나오지 않는다
    private static MembershipRefundExecutionResult moreConservative(MembershipRefundExecutionResult current,
                                                                    MembershipRefundExecutionResult next) {
        if (current == null) {
            return next;
        }
        return rank(next) < rank(current) ? next : current;
    }

    private static int rank(MembershipRefundExecutionResult result) {
        return switch (result) {
            case REVIEW_REQUIRED -> 0;
            case PENDING -> 1;
            case COMPLETED -> 2;
            case FAILED -> 3;
            case SKIPPED -> 4;
        };
    }

    // ---------- 평가 ----------

    private MembershipRefundApplyResult evaluate(Locked locked, MembershipRefund refund, PortOnePayment observed,
                                                 LocalDateTime now) {
        MembershipPaymentAttempt attempt = locked.attempt();
        if (attempt.isSettlementBlocked()) {
            return MembershipRefundApplyResult.of(MembershipRefundExecutionResult.REVIEW_REQUIRED);
        }
        boolean requested = refund.getStatus() == MembershipRefundStatus.REQUESTED;
        if (requested && refund.isSettlementBlocked()) {
            return MembershipRefundApplyResult.of(MembershipRefundExecutionResult.REVIEW_REQUIRED);
        }
        // 확정 환불도 다른 상점·통화·원수납액의 결제를 근거로 기존 결과를 유지하지 않는다
        MembershipRefundApplyResult identityRejected = rejectByIdentity(refund, attempt, observed, now);
        if (identityRejected != null) {
            return identityRejected;
        }
        if (!requested) {
            return evaluateFinal(refund, attempt, observed, now);
        }
        CancelledSum sum = MembershipRefundCancellationMatcher.cancelledSum(observed);
        if (sum == CancelledSum.UNTRUSTED) {
            log.warn("취소 내역을 신뢰할 수 없음(알 수 없는 상태·중복 ID·금액 누락): refundId={}", refund.getId());
            return MembershipRefundApplyResult.signal(Signal.UNTRUSTED_OBSERVATION);
        }
        if (sum == CancelledSum.INCONSISTENT) {
            return MembershipRefundApplyResult.of(MembershipRefundExecutionResult.PENDING);
        }
        RefundMatchSnapshot snapshot = RefundMatchSnapshot.of(refund, attempt.getPortonePaymentId(),
            portOneProperties.storeId());
        List<RefundCancellationBinding> bindings = locked.refunds().stream().map(RefundCancellationBinding::of).toList();
        Match match = MembershipRefundCancellationMatcher.match(snapshot, observed, bindings);
        return switch (match.kind()) {
            case CONTRADICTION, AMBIGUOUS, FOREIGN_LINKED -> blockEvidenceConflict(refund, attempt, now, match.kind().name());
            case EXACT_ID, BY_REASON -> match.unexplainedActivity()
                // 자기 취소와 함께 어느 환불로도 설명되지 않는 진행·성공 취소가 있다 — 자동 완료하지 않고 운영이 대조한다
                ? blockEvidenceConflict(refund, attempt, now, "UNEXPLAINED_ACTIVITY")
                : applyMatched(locked, refund, match.cancellation(), now);
            case NONE -> {
                if (refund.getPortoneCancellationId() != null || match.unexplainedActivity()) {
                    // 저장된 취소 ID가 아직 조회에 보이지 않거나, 어느 환불로도 설명되지 않는 진행·성공 취소가 있다 — 새 POST 금지, 재조회
                    yield MembershipRefundApplyResult.of(MembershipRefundExecutionResult.PENDING);
                }
                if (refund.getPgIdempotencyKey() == null) {
                    yield MembershipRefundApplyResult.signal(Signal.LEGACY_UNPROVEN);
                }
                if (refund.isAutoHalted()) {
                    yield MembershipRefundApplyResult.of(MembershipRefundExecutionResult.REVIEW_REQUIRED);
                }
                yield MembershipRefundApplyResult.pendingPostAllowed();
            }
        };
    }

    // PG 식별 검사. MATCH면 null — 계속 판정한다. INCOMPLETE는 이번 관측만 미확정(PENDING)으로 보고 저장된 상태는 그대로 둔다
    private MembershipRefundApplyResult rejectByIdentity(MembershipRefund refund, MembershipPaymentAttempt attempt,
                                                         PortOnePayment observed, LocalDateTime now) {
        RefundMatchSnapshot snapshot = RefundMatchSnapshot.of(refund, attempt.getPortonePaymentId(),
            portOneProperties.storeId());
        Identity identity = MembershipRefundCancellationMatcher.identity(snapshot, observed);
        switch (identity) {
            case INCOMPLETE -> {
                return MembershipRefundApplyResult.of(MembershipRefundExecutionResult.PENDING);
            }
            case MISMATCH -> {
                refund.requireOpsReview(MembershipRefundOpsReason.PG_IDENTITY_MISMATCH, now);
                attempt.blockSettlement(now, MembershipOpsReviewReason.REFUND_EVIDENCE_CONFLICT);
                log.error("환불 대상 결제의 PG 식별값(상점·통화·원수납액)이 저장 근거와 다름 → 강한 차단(운영 확인): refundId={}, attemptId={}",
                    refund.getId(), attempt.getId());
                return MembershipRefundApplyResult.of(MembershipRefundExecutionResult.REVIEW_REQUIRED);
            }
            case PAYMENT_FAILED -> {
                refund.requireOpsReview(MembershipRefundOpsReason.EVIDENCE_CONFLICT, now);
                attempt.blockSettlement(now, MembershipOpsReviewReason.PAID_EVIDENCE_CONFLICT);
                log.error("수납으로 기록된 시도를 PG가 FAILED라고 함 → 강한 차단(운영 확인): refundId={}, attemptId={}", refund.getId(),
                    attempt.getId());
                return MembershipRefundApplyResult.of(MembershipRefundExecutionResult.REVIEW_REQUIRED);
            }
            default -> {
                return null;
            }
        }
    }

    // 매칭된 취소 내역으로 확정. 금액은 저장 환불액과 같아야 한다. SUCCEEDED → COMPLETED(+MEMBER 종료), FAILED → FAILED,
    // REQUESTED → 취소 ID 기록·재조회
    private MembershipRefundApplyResult applyMatched(Locked locked, MembershipRefund refund, PortOneCancellation c,
                                                     LocalDateTime now) {
        MembershipPaymentAttempt attempt = locked.attempt();
        if (c.totalAmount() == null || !c.totalAmount().equals(refund.getAmount())) {
            return blockEvidenceConflict(refund, attempt, now, "CANCELLATION_AMOUNT");
        }
        if (c.status() == PortOneCancellationStatus.SUCCEEDED) {
            if (refund.getRequestedBy() == RefundRequester.MEMBER
                && !refund.getPeriodEndAt().equals(locked.membership().endAt())) {
                // 이용권이 환불 대상 회차보다 새 유료 기간으로 진행했다 — 어느 기간을 종료할지 자동으로 정하지 않는다
                refund.requireOpsReview(MembershipRefundOpsReason.TARGET_PERIOD_MISMATCH, now);
                attempt.blockSettlement(now, MembershipOpsReviewReason.REFUND_TARGET_PERIOD_MISMATCH);
                log.error("MEMBER 환불 완료 시점의 이용권 기간이 환불 대상 회차와 다름 → 강한 차단(운영 확인): refundId={}, membershipId={}",
                    refund.getId(), locked.membership().membershipId());
                return MembershipRefundApplyResult.of(MembershipRefundExecutionResult.REVIEW_REQUIRED);
            }
            refund.markCompleted(now, c.canceledAt(), c.id());
            if (refund.getRequestedBy() == RefundRequester.MEMBER) {
                membershipBillingService.terminateAfterRefund(locked.membership().membershipId(), now);
            }
            log.info("멤버십 환불 완료: refundId={}, requestedBy={}, amount={}, cancellationId={}", refund.getId(),
                refund.getRequestedBy(), refund.getAmount(), c.id());
            return MembershipRefundApplyResult.of(MembershipRefundExecutionResult.COMPLETED);
        }
        if (c.status() == PortOneCancellationStatus.FAILED) {
            refund.markFailed(CODE_PG_CANCEL_FAILED, c.id());
            log.warn("멤버십 환불 실패 확정: refundId={}, requestedBy={}, cancellationId={}", refund.getId(),
                refund.getRequestedBy(), c.id());
            return MembershipRefundApplyResult.of(MembershipRefundExecutionResult.FAILED);
        }
        if (c.status() == PortOneCancellationStatus.REQUESTED) {
            if (!refund.recordCancellationId(c.id())) {
                return blockEvidenceConflict(refund, attempt, now, "CANCELLATION_ID");
            }
            return MembershipRefundApplyResult.of(MembershipRefundExecutionResult.PENDING);
        }
        return MembershipRefundApplyResult.signal(Signal.UNTRUSTED_OBSERVATION);
    }

    // 확정 행(COMPLETED·FAILED)에 다시 온 관측. 같은 ID·같은 상태·같은 금액이면 멱등, 보이지 않으면 변경 없음,
    // 다른 상태·금액이나 같은 reason의 다른 취소는 증거 충돌 — FAILED의 모순 성공을 후속 행에 맞춰 해석하지 않는다

    private MembershipRefundApplyResult evaluateFinal(MembershipRefund refund, MembershipPaymentAttempt attempt,
                                                      PortOnePayment observed, LocalDateTime now) {
        MembershipRefundExecutionResult idempotent = refund.getStatus() == MembershipRefundStatus.COMPLETED
            ? MembershipRefundExecutionResult.COMPLETED : MembershipRefundExecutionResult.FAILED;

        // 확정 환불도 취소 ID가 누락된 PG 관측값으로는 정합성을 판정하지 않는다.
        if (observed.cancellations().stream()
            .anyMatch(c -> c.id() == null || c.id().isBlank())) {
            return MembershipRefundApplyResult.signal(Signal.UNTRUSTED_OBSERVATION);
        }

        // 같은 취소 ID가 두 번 오면 어느 쪽 상태·금액이 맞는지 알 수 없어 판정하지 않는다
        if (observed.cancellations().stream()
            .map(PortOneCancellation::id)
            .distinct()
            .count() != observed.cancellations().size()) {
            return MembershipRefundApplyResult.signal(Signal.UNTRUSTED_OBSERVATION);
        }

        if (refund.getPgRequestReason() != null) {
            boolean otherWithSameReason = observed.cancellations().stream()
                .filter(c -> c.trigger() == PortOneCancellationTrigger.API)
                .filter(c -> refund.getPgRequestReason().equals(c.reason()))
                .filter(c -> c.status() == PortOneCancellationStatus.SUCCEEDED
                    || c.status() == PortOneCancellationStatus.REQUESTED)
                .anyMatch(c -> !Objects.equals(c.id(), refund.getPortoneCancellationId()));

            if (otherWithSameReason) {
                return blockEvidenceConflict(refund, attempt, now, "DUPLICATE_REASON");
            }
        }

        if (refund.getPortoneCancellationId() == null) {
            return MembershipRefundApplyResult.of(idempotent);
        }

        PortOneCancellation seen = observed.cancellations().stream()
            .filter(c -> refund.getPortoneCancellationId().equals(c.id()))
            .findFirst().orElse(null);

        if (seen == null) {
            return MembershipRefundApplyResult.of(idempotent);
        }

        boolean sameAmount = seen.totalAmount() != null && seen.totalAmount().equals(refund.getAmount());

        if (refund.getStatus() == MembershipRefundStatus.COMPLETED) {
            return seen.status() == PortOneCancellationStatus.SUCCEEDED && sameAmount
                ? MembershipRefundApplyResult.of(idempotent)
                : blockEvidenceConflict(refund, attempt, now, "COMPLETED_" + seen.status());
        }

        return seen.status() == PortOneCancellationStatus.FAILED
            ? MembershipRefundApplyResult.of(idempotent)
            : blockEvidenceConflict(refund, attempt, now, "FAILED_" + seen.status());
    }


    private static MembershipRefundApplyResult blockEvidenceConflict(MembershipRefund refund,
                                                                     MembershipPaymentAttempt attempt,
                                                                     LocalDateTime now, String cause) {
        refund.requireOpsReview(MembershipRefundOpsReason.EVIDENCE_CONFLICT, now);
        attempt.blockSettlement(now, MembershipOpsReviewReason.REFUND_EVIDENCE_CONFLICT);
        log.error("환불 근거와 PG 취소 내역이 모순 → 강한 차단(운영 확인): refundId={}, attemptId={}, cause={}", refund.getId(),
            attempt.getId(), cause);
        return MembershipRefundApplyResult.of(MembershipRefundExecutionResult.REVIEW_REQUIRED);
    }

    // ---------- 잠금 ----------

    private record Locked(Long memberId, boolean withdrawn, BillingMembershipData membership, MembershipPayment payment,
                          MembershipPaymentAttempt attempt, List<MembershipPaymentAttempt> attempts,
                          List<MembershipRefund> refunds) {
    }

    // 첫 DB 작업이 회원 잠금이다(잠금 전 일반 조회 없음)
    private Locked lock(Long memberId, Long attemptId) {
        MemberSettlementLock member = memberService.lockMemberForSettlement(memberId);
        MembershipPaymentAttemptRef ref = attempts.findRef(attemptId)
            .orElseThrow(() -> new BusinessException(PaymentErrorCode.MEMBERSHIP_PAYMENT_NOT_FOUND));
        BillingMembershipData membership =
            membershipBillingService.getBillingMembershipForUpdate(memberId, ref.membershipId());
        MembershipPayment payment = payments.findByIdForUpdate(ref.membershipPaymentId())
            .orElseThrow(() -> new BusinessException(PaymentErrorCode.MEMBERSHIP_PAYMENT_NOT_FOUND));
        List<MembershipPaymentAttempt> all = rules.lockAttempts(payment);
        MembershipPaymentAttempt target = all.stream().filter(a -> a.getId().equals(attemptId)).findFirst()
            .orElseThrow(() -> new IllegalStateException("시도·회차 연결이 맞지 않음: attemptId=" + attemptId));
        if (!payment.getMembershipId().equals(membership.membershipId())) {
            throw new IllegalStateException("시도·회차·이용권 연결이 맞지 않음: attemptId=" + attemptId);
        }
        List<MembershipRefund> locked = new ArrayList<>();
        for (Long id : refunds.findIdsByMembershipPaymentAttemptIdOrderByIdAsc(attemptId)) {
            MembershipRefund refund = refunds.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalStateException("잠그려던 환불 행이 사라짐: refundId=" + id));
            if (!refund.getMembershipPaymentAttempt().getId().equals(attemptId)) {
                throw new IllegalStateException("환불·시도 연결이 맞지 않음: refundId=" + id);
            }
            locked.add(refund);
        }
        return new Locked(memberId, member.withdrawn(), membership, payment, target, all, locked);
    }
}
