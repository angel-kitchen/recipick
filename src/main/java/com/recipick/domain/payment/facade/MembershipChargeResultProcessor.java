package com.recipick.domain.payment.facade;

import com.recipick.domain.member.dto.BillingMembershipData;
import com.recipick.domain.member.dto.MembershipBillingResult;
import com.recipick.domain.member.entity.enums.MembershipSource;
import com.recipick.domain.member.entity.enums.MembershipType;
import com.recipick.domain.member.service.MemberService;
import com.recipick.domain.member.service.MembershipBillingService;
import com.recipick.domain.payment.config.MembershipBillingGate;
import com.recipick.domain.payment.config.MembershipBillingProperties;
import com.recipick.domain.payment.entity.BillingKey;
import com.recipick.domain.payment.entity.MembershipPayment;
import com.recipick.domain.payment.entity.MembershipPaymentAttempt;
import com.recipick.domain.payment.entity.MembershipRefund;
import com.recipick.domain.payment.entity.enums.BillingKeyStatus;
import com.recipick.domain.payment.entity.enums.MembershipOpsReviewReason;
import com.recipick.domain.payment.entity.enums.MembershipPaymentAttemptStatus;
import com.recipick.domain.payment.entity.enums.MembershipPaymentStatus;
import com.recipick.domain.payment.entity.enums.MembershipRefundStatus;
import com.recipick.domain.payment.exception.MembershipSettlementInvariantException;
import com.recipick.domain.payment.exception.MembershipSettlementInvariantException.Kind;
import com.recipick.domain.payment.exception.PaymentErrorCode;
import com.recipick.domain.payment.repository.BillingKeyRepository;
import com.recipick.domain.payment.repository.MembershipPaymentAttemptRepository;
import com.recipick.domain.payment.repository.MembershipPaymentRepository;
import com.recipick.domain.payment.repository.MembershipRefundRepository;
import com.recipick.domain.payment.service.MembershipBillingPeriods;
import com.recipick.domain.payment.service.MembershipChargeOutcome;
import com.recipick.domain.payment.service.MembershipPaymentAttemptRef;
import com.recipick.domain.payment.service.MembershipPgVerdict;
import com.recipick.global.common.PolicyConstants;
import com.recipick.global.exception.BusinessException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// TX2 — 결과 반영. 잠금 순서 MEMBER → 이용권(C-7) → 회차 PK → 시도 PK. 결과 기록과 이용권 반영을
// 한 트랜잭션에서 커밋한다 — 회원 도메인 반영이 예외로 끝나면 결제 상태 기록도 함께 롤백되고 복구가 같은 결제 ID로 다시 한다.
// 첫 결제 갱신 공용 진입점이다. 회차 종류는 회차 자체(graceDeadlineAt null = 첫 결제)로 가린다 — 이용권의 activatedAt으로
// 가리지 않는다: 첫 결제 회차의 PAID는 반드시 활성화·환불 요청·정합성 예외 중 하나로 끝나야 하고 조용히 건너뛰면 안 된다.
// 명시적으로 판정한 정합성 오류(0b·I-22 위반·사유 없는 CONFLICT)는 MembershipSettlementInvariantException으로 전체 롤백 + ERROR
// (ID·코드만). 복구는 이 예외만 잡아 flagInconsistent로 원인을 재검증한 뒤 운영 표시를 남긴다.
// 그 밖의 IllegalStateException(연결 불일치·허용되지 않는 전이)은 자동 정합화 중단 대상이 아니다.
// 로그에는 ID·상태·고정 코드·금액 숫자만 남긴다
@Slf4j
@Component
@RequiredArgsConstructor
public class MembershipChargeResultProcessor {

    private static final String REASON_NOT_DISPATCHED = "NOT_DISPATCHED";
    private static final int FIRST_SYSTEM_REFUND_SEQUENCE = 1;

    private final MemberService memberService;
    private final MembershipBillingService membershipBillingService;
    private final MembershipPaymentRepository payments;
    private final MembershipPaymentAttemptRepository attempts;
    private final MembershipRefundRepository refunds;
    private final BillingKeyRepository billingKeys;
    private final MembershipBillingProperties properties;
    private final MembershipBillingGate gate;
    private final Clock clock;

    @Transactional
    public MembershipChargeOutcome apply(Long memberId, Long attemptId, MembershipPgVerdict verdict) {
        LocalDateTime now = LocalDateTime.now(clock);
        Locked locked = lock(memberId, attemptId);
        MembershipPayment payment = locked.payment();
        MembershipPaymentAttempt attempt = locked.attempt();
        if (attempt.isFinal()) {
            return MembershipChargeOutcome.ALREADY_APPLIED;
        }
        if (attempt.isAutoReconcileHalted()) {
            // 정합성 오류로 운영 확인 대상이 된 시도. 표시 이전에 조회해 둔 작업(웹훅·복구·재요청)이 여기서 자동 반영하지 않게 막는다
            log.warn("자동 정합화가 멈춘 시도의 결과 반영 요청을 건너뜀(운영 확인 대상): attemptId={}", attemptId);
            return MembershipChargeOutcome.RECONCILE_HALTED;
        }
        if (attempt.getStatus() == MembershipPaymentAttemptStatus.READY) {
            // PG에 간 적 없는 시도의 결과는 판정하지 않는다(임대 만료 규칙이 처리)
            return MembershipChargeOutcome.NOT_DISPATCHED;
        }
        requireFirstPayment(payment, attemptId);
        return switch (verdict.kind()) {
            case PAID -> applyPaid(locked, verdict, now);
            case PAID_AMOUNT_MISMATCH -> applyAmountMismatch(locked, verdict, now);
            case FAILED -> applyFailed(locked, verdict, now);
            case UNRESOLVED, NOT_FOUND -> keepUnknown(locked, verdict);
            case UNTRUSTED -> manualReview(locked, verdict, now);
        };
    }

    // READY 임대 만료. 같은 잠금 순서.
    @Transactional
    public ExpiredReadyResult handleExpiredReady(Long memberId, Long attemptId, LocalDateTime now) {
        Locked locked = lock(memberId, attemptId);
        MembershipPayment payment = locked.payment();
        MembershipPaymentAttempt attempt = locked.attempt();
        if (!attempt.cancelNotDispatched(now)) {
            return new ExpiredReadyResult(outcomeOfUncancelable(attempt, now), attempt.getId());
        }
        if (!payment.isUnresolved()) {
            // ⑦ 확정 회차. 다른 READY가 남아 있어도(비정상) 실행 대상으로 돌려주지 않는다 — claim()은 회차 상태를 보지 않으므로 여기서 막는다
            return new ExpiredReadyResult(MembershipChargeOutcome.ALREADY_APPLIED, null);
        }
        requireFirstPayment(payment, attemptId);
        List<MembershipPaymentAttempt> all = attempts.findByMembershipPaymentIdOrderByAttemptNoAsc(payment.getId());
        // ⑧-0: PG에 갔을 수 있는 시도가 남아 있으면 새 ID를 만들지 않고 회차도 닫지 않는다(재조회·운영 확인 대기)
        boolean pgMayHaveSeen = all.stream().anyMatch(a -> a.getStatus() == MembershipPaymentAttemptStatus.DISPATCHING
            || a.getStatus() == MembershipPaymentAttemptStatus.UNKNOWN
            || a.getStatus() == MembershipPaymentAttemptStatus.MANUAL_REVIEW);
        if (pgMayHaveSeen) {
            return new ExpiredReadyResult(MembershipChargeOutcome.STILL_UNKNOWN, null);
        }
        LocalDateTime acceptUntil = MembershipBillingPeriods.firstChargeAcceptUntil(all.get(0).getRequestedAt(),
            properties.firstChargeAcceptWindow());
        Optional<MembershipPaymentAttempt> otherReady = all.stream()
            .filter(a -> a.getStatus() == MembershipPaymentAttemptStatus.READY && now.isBefore(a.getLeaseExpiresAt()))
            .findFirst();
        if (otherReady.isPresent()) {
            return new ExpiredReadyResult(MembershipChargeOutcome.RETRY_READY, otherReady.get().getId());
        }
        BillingMembershipData membership = locked.membership();
        Optional<Long> activeKeyId = billingKeys.findIdByMemberIdAndStatus(memberId, BillingKeyStatus.ACTIVE);
        boolean allowed = gate.newChargeEnabled()
            && membership.type() == MembershipType.PAID && membership.activatedAt() == null
            && membership.terminatedAt() == null
            && membership.consent() != null && membership.consent().consentedAmount() != null
            && membership.consent().consentedAmount() == PolicyConstants.MEMBERSHIP_MONTHLY_FEE
            && activeKeyId.isPresent()
            && now.isBefore(acceptUntil)
            && all.size() < properties.firstChargeMaxAttempts();
        if (allowed) {
            int nextNo = all.get(all.size() - 1).getAttemptNo() + 1;
            BillingKey key = billingKeys.getReferenceById(activeKeyId.get());
            MembershipPaymentAttempt next = attempts.saveAndFlush(MembershipPaymentAttempt.ready(payment, nextNo, key,
                now, MembershipBillingPeriods.firstChargeLeaseUntil(now, properties.lease(), acceptUntil)));
            log.info("첫 결제 READY 임대 만료 → 새 시도 접수: membershipPaymentId={}, attemptNo={}", payment.getId(), nextNo);
            return new ExpiredReadyResult(MembershipChargeOutcome.RETRY_READY, next.getId());
        }
        payment.markFailed(REASON_NOT_DISPATCHED);
        MembershipBillingResult closed =
            membershipBillingService.applyFirstPaymentFailed(payment.getMembershipId(), now);
        if (closed == MembershipBillingResult.CONFLICT) {
            throw new IllegalStateException("PG에 간 적 없는 첫 결제 회차를 닫는데 이용권이 충돌: membershipId="
                + payment.getMembershipId());
        }
        log.info("첫 결제 READY 임대 만료 → 회차 종료(재접수 불가): membershipPaymentId={}, attempts={}", payment.getId(),
            all.size());
        return new ExpiredReadyResult(MembershipChargeOutcome.CLOSED_UNPAID, null);
    }

    // cancelNotDispatched가 false인 시도의 상태별 결과 — 아무것도 바꾸지 않는다
    private static MembershipChargeOutcome outcomeOfUncancelable(MembershipPaymentAttempt attempt, LocalDateTime now) {
        return switch (attempt.getStatus()) {
            case READY -> MembershipChargeOutcome.IN_PROGRESS;           // 임대가 아직 안 끝남(만료 아님)
            case DISPATCHING -> MembershipChargeOutcome.IN_PROGRESS;     // 다른 Worker가 선점
            case UNKNOWN -> MembershipChargeOutcome.STILL_UNKNOWN;
            case MANUAL_REVIEW -> MembershipChargeOutcome.MANUAL_REVIEW;
            default -> MembershipChargeOutcome.ALREADY_APPLIED;          // PAID·FAILED·CANCELED
        };
    }

    public record ExpiredReadyResult(MembershipChargeOutcome outcome, Long attemptId) {
    }

    // 정합성 예외로 TX2가 롤백된 뒤 복구가 여는 별도의 짧은 TX. MEMBER부터 같은 잠금 순서로 다시
    // 잠그고 최신 상태로 원인을 재검증한다 — 그 사이 다른 처리(웹훅·운영 조치)로 해결됐으면 아무것도 남기지 않는다.
    // 원인이 그대로면 상태·회차·이용권·환불은 바꾸지 않고 시도에 정합성 코드 + 운영 표시만 남겨 자동 정합화를 멈춘다(isAutoReconcileHalted)
    @Transactional
    public boolean flagInconsistent(Long memberId, Long attemptId, Kind kind, LocalDateTime now) {
        Locked locked = lock(memberId, attemptId);
        MembershipPayment payment = locked.payment();
        MembershipPaymentAttempt attempt = locked.attempt();
        if (attempt.getStatus() != MembershipPaymentAttemptStatus.DISPATCHING
            && attempt.getStatus() != MembershipPaymentAttemptStatus.UNKNOWN) {
            return false;
        }
        if (attempt.isAutoReconcileHalted() || !payment.isUnresolved() || payment.isApplied()
            || !payment.isFirstPayment() || !invariantStillBroken(locked, kind)) {
            log.info("정합성 예외의 원인이 더 이상 없어 운영 표시를 남기지 않음: attemptId={}, kind={}", attemptId, kind);
            return false;
        }
        attempt.markAppliedStateInconsistent(now);
        log.error("정합성 예외 시도의 자동 정합화 중단(운영 확인 대상): attemptId={}, membershipPaymentId={}, kind={}",
            attemptId, payment.getId(), kind);
        return true;
    }

    // 표시 TX의 원인 재검증. 0b: 이용권은 활성인데 근거 회차가 없다. 사유 없는 CONFLICT: 종료·상태·소유 사유가 여전히 확인되지 않는다
    // (C-3a를 다시 부르면 상태가 바뀔 수 있어 부르지 않는다 — 사유가 생겼으면 다음 정합화가 정상 환불 경로로 처리한다)
    private boolean invariantStillBroken(Locked locked, Kind kind) {
        MembershipPayment payment = locked.payment();
        return switch (kind) {
            case ACTIVATED_WITHOUT_APPLIED_PAYMENT -> locked.membership().activatedAt() != null
                && !payments.existsByMembershipIdAndMembershipAppliedAtIsNotNullAndIdNot(payment.getMembershipId(),
                payment.getId());
            case UNEXPLAINED_FIRST_PAYMENT_CONFLICT -> !conflictReasonConfirmed(locked);
        };
    }

    // 3단계에는 갱신 회차(graceDeadlineAt non-null)를 만드는 경로가 없다 — 만나면 데이터 이상이라 롤백 + ERROR(ID만).
    // 4단계가 갱신 분기를 같은 Processor에 추가한다
    private static void requireFirstPayment(MembershipPayment payment, Long attemptId) {
        if (!payment.isFirstPayment()) {
            log.error("3단계가 모르는 갱신 회차의 시도: attemptId={}, membershipPaymentId={}", attemptId, payment.getId());
            throw new IllegalStateException("갱신 회차는 3단계 범위가 아님: membershipPaymentId=" + payment.getId());
        }
    }

    // ---------- 잠금 ----------

    private record Locked(Long memberId, BillingMembershipData membership, MembershipPayment payment,
                          MembershipPaymentAttempt attempt) {
    }

    // ① MEMBER → ② ID만 일반 조회 → ③ 이용권 PK(C-7, 소유 재검증) → ④ 회차 PK → ⑤ 시도 PK → ⑥ 연결 검증
    private Locked lock(Long memberId, Long attemptId) {
        memberService.lockActiveMember(memberId);
        MembershipPaymentAttemptRef ref = attempts.findRef(attemptId)
            .orElseThrow(() -> new BusinessException(PaymentErrorCode.MEMBERSHIP_PAYMENT_NOT_FOUND));
        BillingMembershipData membership =
            membershipBillingService.getBillingMembershipForUpdate(memberId, ref.membershipId());
        MembershipPayment payment = payments.findByIdForUpdate(ref.membershipPaymentId())
            .orElseThrow(() -> new BusinessException(PaymentErrorCode.MEMBERSHIP_PAYMENT_NOT_FOUND));
        MembershipPaymentAttempt attempt = attempts.findByIdForUpdate(attemptId)
            .orElseThrow(() -> new BusinessException(PaymentErrorCode.MEMBERSHIP_PAYMENT_NOT_FOUND));
        if (!attempt.getMembershipPayment().getId().equals(payment.getId())
            || !payment.getMembershipId().equals(membership.membershipId())) {
            throw new IllegalStateException("시도·회차·이용권 연결이 맞지 않음: attemptId=" + attemptId);
        }
        return new Locked(memberId, membership, payment, attempt);
    }

    // ---------- 판정 분기(5-1) ----------

    private MembershipChargeOutcome applyPaid(Locked locked, MembershipPgVerdict verdict, LocalDateTime now) {
        MembershipPayment payment = locked.payment();
        MembershipPaymentAttempt attempt = locked.attempt();
        if (!payment.isUnresolved()) {
            // 확정 회차(PAID·FAILED)에 온 늦은 PAID(LC 4-6 마지막 두 행). 회차 상태는 바꾸지 않고(I-16) 돈만 돌려준다
            return refundOnClosedPayment(locked, verdict, now);
        }
        Optional<MembershipChargeOutcome> appliedState = checkAppliedState(locked, verdict, now);
        if (appliedState.isPresent()) {
            return appliedState.get();
        }
        MembershipBillingPeriods.FirstPeriod period = MembershipBillingPeriods.firstPeriod(verdict.pgPaidAt());
        if (!now.isBefore(period.endAt())) {
            // 5-1 3번: 첫 결제기간이 이미 끝남 — 활성화하지 않고 전액(실제 수납액) 환불
            return closeUnappliedPaid(locked, verdict, now, MembershipOpsReviewReason.FIRST_PAYMENT_PERIOD_ENDED,
                MembershipOpsReviewReason.FIRST_PAYMENT_PERIOD_ENDED.name(), true);
        }
        MembershipBillingResult result = membershipBillingService.applyFirstPaymentPaid(payment.getMembershipId(),
            verdict.pgPaidAt(), period.endAt(), period.graceEndAt());
        switch (result) {
            case ACTIVATED -> {
                attempt.markPaid(verdict.pgPaidAt(), now);
                payment.applyFirstPeriod(period.periodStart(), period.periodEnd());
                payment.markPaid(verdict.pgPaidAt(), now);
                if (!Duration.between(verdict.pgPaidAt(), now).minus(properties.longDelay()).isNegative()) {
                    // 5-1 2번: 확인 지연 D 이상 — PG 시각 기준으로 활성화하되 보상 판단은 운영에
                    attempt.flagOpsReview(now, MembershipOpsReviewReason.FIRST_PAYMENT_LONG_DELAY);
                }
                return MembershipChargeOutcome.ACTIVATED;
            }
            case ALREADY_APPLIED -> {
                log.error("이용권은 활성화됐는데 첫 결제 회차가 미반영(I-22 위반) → 롤백: membershipPaymentId={}, attemptId={}",
                    payment.getId(), attempt.getId());
                throw new MembershipSettlementInvariantException(Kind.ACTIVATED_WITHOUT_APPLIED_PAYMENT,
                    payment.getId(), attempt.getId());
            }
            case CONFLICT -> {
                if (!conflictReasonConfirmed(locked)) {
                    log.error("사유를 확인할 수 없는 C-3a CONFLICT → 롤백: membershipPaymentId={}, attemptId={}",
                        payment.getId(), attempt.getId());
                    throw new MembershipSettlementInvariantException(Kind.UNEXPLAINED_FIRST_PAYMENT_CONFLICT,
                        payment.getId(), attempt.getId());
                }
                // 5-1 4번: 종료·상태·소유 충돌이 확인된 수납 → 반영 없이 환불
                return closeUnappliedPaid(locked, verdict, now, MembershipOpsReviewReason.PAID_CONFLICT,
                    MembershipOpsReviewReason.PAID_CONFLICT.name(), false);
            }
            default -> throw new IllegalStateException("첫 결제 반영에서 올 수 없는 결과 " + result
                + ": membershipPaymentId=" + payment.getId());
        }
    }

    // 신뢰 가능한 PAID인데 KRW 수납액이 청구액과 다름 → 활성화하지 않고 실제 수납액을 돌려준다
    private MembershipChargeOutcome applyAmountMismatch(Locked locked, MembershipPgVerdict verdict, LocalDateTime now) {
        MembershipPayment payment = locked.payment();
        log.error("첫 결제 수납액 불일치: membershipPaymentId={}, expected={}, actual={}", payment.getId(),
            payment.getAmount(), verdict.paidAmount());
        if (!payment.isUnresolved()) {
            return refundOnClosedPayment(locked, verdict, now);
        }
        Optional<MembershipChargeOutcome> appliedState = checkAppliedState(locked, verdict, now);
        if (appliedState.isPresent()) {
            return appliedState.get();
        }
        return closeUnappliedPaid(locked, verdict, now, MembershipOpsReviewReason.PAID_AMOUNT_MISMATCH,
            MembershipPgVerdict.CODE_AMOUNT_MISMATCH, true);
    }

    // 첫 결제 회차가 미반영인데 이용권이 이미 활성. 0a — 활성화의 근거가 된 다른 회차가 있다
    // (중복 수납) → 반영 없이 실제 수납액 SYSTEM 환불. 0b — 근거 회차가 없다(I-22·J-1 위반 데이터) → 시도·회차 상태 전이도 환불도 없이
    // 정합성 예외로 TX2 전체 롤백(이 PAID가 실제 활성화의 대가일 수 있어 자동 환불하지 않는다). 미해결이 유지되니 C-17이 계속 막고
    // 복구가 그 예외를 잡아 별도 TX(flagInconsistent)로 원인을 재검증한 뒤 운영 표시를 남겨 자동 정합화를 멈춘다 — 자동 재시도는 사실상 1회
    private Optional<MembershipChargeOutcome> checkAppliedState(Locked locked, MembershipPgVerdict verdict,
                                                                LocalDateTime now) {
        MembershipPayment payment = locked.payment();
        if (locked.membership().activatedAt() == null || payment.isApplied()) {
            return Optional.empty();
        }
        boolean appliedByOther = payments.existsByMembershipIdAndMembershipAppliedAtIsNotNullAndIdNot(
            payment.getMembershipId(), payment.getId());
        if (appliedByOther) {
            log.error("활성 이용권에 첫 결제 회차 PAID가 또 옴(중복 수납) → SYSTEM 환불: membershipPaymentId={}, amount={}",
                payment.getId(), verdict.paidAmount());
            return Optional.of(closeUnappliedPaid(locked, verdict, now, MembershipOpsReviewReason.PAID_CONFLICT,
                MembershipOpsReviewReason.PAID_CONFLICT.name(), false));
        }
        MembershipPaymentAttempt attempt = locked.attempt();
        log.error("이용권은 활성인데 근거 회차가 없고 첫 결제 회차는 미반영(5-1 0b, I-22 위반) → 롤백·운영 정합화: membershipId={}, "
                + "membershipPaymentId={}, attemptId={}, verdict={}", payment.getMembershipId(), payment.getId(),
            attempt.getId(), verdict.kind());
        throw new MembershipSettlementInvariantException(Kind.ACTIVATED_WITHOUT_APPLIED_PAYMENT, payment.getId(),
            attempt.getId());
    }

    // 5-1 7번: 재조회로 확정된 실패. 대기 이용권 종료(C-3b). 이미 종료(ALREADY_APPLIED)도 정상, CONFLICT(활성화된 행)는 데이터 이상
    private MembershipChargeOutcome applyFailed(Locked locked, MembershipPgVerdict verdict, LocalDateTime now) {
        MembershipPayment payment = locked.payment();
        MembershipPaymentAttempt attempt = locked.attempt();
        attempt.markFailed(verdict.code(), now);
        if (!payment.isUnresolved()) {
            // 확정 회차의 다른 시도가 실패로 끝남. 회차·이용권은 그대로(I-16)
            return MembershipChargeOutcome.ALREADY_APPLIED;
        }
        payment.markFailed(verdict.code());
        MembershipBillingResult closed =
            membershipBillingService.applyFirstPaymentFailed(payment.getMembershipId(), now);
        if (closed == MembershipBillingResult.CONFLICT) {
            throw new IllegalStateException("실패한 첫 결제의 이용권이 이미 활성화됨(C-3b CONFLICT): membershipPaymentId="
                + payment.getId());
        }
        return MembershipChargeOutcome.CLOSED_UNPAID;
    }

    // 결과 불명·404·조회 장애. 상태만 UNKNOWN으로 두고 복구가 같은 ID로 재조회한다. MANUAL_REVIEW 시도는 그대로 둔다
    private MembershipChargeOutcome keepUnknown(Locked locked, MembershipPgVerdict verdict) {
        MembershipPaymentAttempt attempt = locked.attempt();
        MembershipPayment payment = locked.payment();
        if (attempt.getStatus() == MembershipPaymentAttemptStatus.MANUAL_REVIEW) {
            return MembershipChargeOutcome.MANUAL_REVIEW;
        }
        attempt.markUnknown(verdict.code());
        if (payment.getStatus() == MembershipPaymentStatus.PENDING
            || payment.getStatus() == MembershipPaymentStatus.UNKNOWN) {
            payment.markUnknown();
        }
        return MembershipChargeOutcome.STILL_UNKNOWN;
    }

    // 신뢰할 수 없는 결과. 환불 금액을 만들지 않고 운영 확인으로. 미해결이라 C-17이 계속 막는다
    private MembershipChargeOutcome manualReview(Locked locked, MembershipPgVerdict verdict, LocalDateTime now) {
        MembershipPaymentAttempt attempt = locked.attempt();
        MembershipPayment payment = locked.payment();
        log.error("신뢰할 수 없는 PG 결과 → 운영 확인: attemptId={}, code={}", attempt.getId(), verdict.code());
        if (attempt.getStatus() != MembershipPaymentAttemptStatus.MANUAL_REVIEW) {
            attempt.markManualReview(verdict.code(), now);
        }
        if (payment.getStatus() == MembershipPaymentStatus.PENDING
            || payment.getStatus() == MembershipPaymentStatus.UNKNOWN) {
            payment.markManualReview();
        }
        return MembershipChargeOutcome.MANUAL_REVIEW;
    }

    // ---------- 반영하지 않는 PAID(LC 4-9): 시도 PAID + 환불 필요 + 회차 FAILED + (C-3b) + SYSTEM 환불 REQUESTED를 한 TX에 ----------

    private MembershipChargeOutcome closeUnappliedPaid(Locked locked, MembershipPgVerdict verdict, LocalDateTime now,
                                                       MembershipOpsReviewReason reason, String failureReason,
                                                       boolean closeMembership) {
        MembershipPayment payment = locked.payment();
        MembershipPaymentAttempt attempt = locked.attempt();
        attempt.markPaid(verdict.pgPaidAt(), now);
        attempt.requireRefund(now, reason);
        payment.closeUnapplied(failureReason);
        if (closeMembership) {
            MembershipBillingResult closed =
                membershipBillingService.applyFirstPaymentFailed(payment.getMembershipId(), now);
            if (closed == MembershipBillingResult.CONFLICT) {
                throw new IllegalStateException("반영하지 않는 첫 결제의 대기 이용권을 닫는데 충돌(C-3b): membershipPaymentId="
                    + payment.getId());
            }
        }
        requestSystemRefund(attempt, verdict.paidAmount(), reason, now);
        log.warn("첫 결제 PAID를 반영하지 않음 → SYSTEM 환불 요청: membershipPaymentId={}, reason={}, amount={}",
            payment.getId(), reason, verdict.paidAmount());
        return MembershipChargeOutcome.UNAPPLIED_REFUND_REQUESTED;
    }

    // 확정 회차에 온 늦은 PAID(LC 4-6 "확정 FAILED·PAID" 행). 회차·이용권은 건드리지 않고 시도에 PAID + 환불 필요만 적는다
    private MembershipChargeOutcome refundOnClosedPayment(Locked locked, MembershipPgVerdict verdict,
                                                          LocalDateTime now) {
        MembershipPaymentAttempt attempt = locked.attempt();
        attempt.markPaid(verdict.pgPaidAt(), now);
        attempt.requireRefund(now, MembershipOpsReviewReason.PAID_ON_CLOSED_PAYMENT);
        requestSystemRefund(attempt, verdict.paidAmount(), MembershipOpsReviewReason.PAID_ON_CLOSED_PAYMENT, now);
        log.error("확정된 회차에 늦은 PAID → SYSTEM 환불 요청: membershipPaymentId={}, attemptId={}, amount={}",
            locked.payment().getId(), attempt.getId(), verdict.paidAmount());
        return MembershipChargeOutcome.UNAPPLIED_REFUND_REQUESTED;
    }

    // 같은 시도에 두 번째 REQUESTED를 만들지 않는다(시도 PK 잠금 아래). 금액은 항상 실제 수납액
    private void requestSystemRefund(MembershipPaymentAttempt attempt, Long actualAmount,
                                     MembershipOpsReviewReason reason, LocalDateTime now) {
        if (refunds.existsByMembershipPaymentAttemptIdAndStatus(attempt.getId(), MembershipRefundStatus.REQUESTED)) {
            return;
        }
        if (actualAmount == null) {
            throw new IllegalStateException("실제 수납액 없이 SYSTEM 환불을 요청할 수 없음: attemptId=" + attempt.getId());
        }
        refunds.save(MembershipRefund.systemRefundRequested(attempt, actualAmount, reason.name(),
            FIRST_SYSTEM_REFUND_SEQUENCE, now));
    }

    // C-3a CONFLICT의 사유 검증(5-1 4번): 종료 / 유료·구독·전환이 아님 / 소유 불일치 중 하나는 확인돼야 한다
    private static boolean conflictReasonConfirmed(Locked locked) {
        BillingMembershipData m = locked.membership();
        boolean terminated = m.terminatedAt() != null;
        boolean wrongState = m.type() != MembershipType.PAID
            || (m.source() != MembershipSource.SUBSCRIBE && m.source() != MembershipSource.TRIAL_CONVERT);
        boolean wrongOwner = !m.memberId().equals(locked.memberId());
        return terminated || wrongState || wrongOwner;
    }
}
