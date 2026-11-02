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
import com.recipick.domain.payment.entity.enums.RefundRequester;
import com.recipick.domain.payment.exception.MembershipSettlementInvariantException;
import com.recipick.domain.payment.exception.MembershipSettlementInvariantException.Kind;
import com.recipick.domain.payment.exception.PaymentErrorCode;
import com.recipick.domain.payment.facade.MembershipRenewalRules.Mode;
import com.recipick.domain.payment.repository.BillingKeyRepository;
import com.recipick.domain.payment.repository.MembershipPaymentAttemptRepository;
import com.recipick.domain.payment.repository.MembershipPaymentRepository;
import com.recipick.domain.payment.repository.MembershipRefundRepository;
import com.recipick.domain.payment.service.MembershipBillingPeriods;
import com.recipick.domain.payment.service.MembershipBillingPeriods.RenewalPeriod;
import com.recipick.domain.payment.service.MembershipChargeOutcome;
import com.recipick.domain.payment.service.MembershipPaymentAttemptRef;
import com.recipick.domain.payment.service.MembershipPgVerdict;
import com.recipick.domain.payment.service.MembershipReconciliationTrigger;
import com.recipick.domain.payment.service.MembershipRenewalDecision;
import com.recipick.global.common.PolicyConstants;
import com.recipick.global.exception.BusinessException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
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
// (ID·코드만). 실행기가 이 예외만 잡아 flagInconsistent로 원인을 재검증한 뒤 강한 차단 표시를 남긴다.
// 그 밖의 IllegalStateException(연결 불일치·허용되지 않는 전이)은 자동 정합화 중단 대상이 아니다.
// 두 회차 종류 모두 회차 PK 뒤에 그 회차의 시도 전부를 PK 오름차순으로 잠그고(대상 시도를 먼저 잠그지 않는다) 잠근 목록으로 판정한다.
// 회차에 강한 정합성 차단 시도가 하나라도 있으면 어떤 결과도 반영하지 않는다. PG에 보낸 적 없는 시도에 PG 거래가 관측되면 그 시도를
// 강하게 차단하고, PG에 보낸 확정 시도의 재전송은 저장된 근거와 대조해 멱등·수납 정정·차단 중 하나로 끝낸다.
// 판정 시각은 잠금을 모두 잡은 뒤 읽는다 — 잠금을 기다리는 사이 마감·기간 종료를 넘겼으면 그 시각으로 본다.
// 로그에는 ID·상태·고정 코드·금액 숫자만 남긴다
@Slf4j
@Component
@RequiredArgsConstructor
public class MembershipChargeResultProcessor {

    private static final String REASON_NOT_DISPATCHED = "NOT_DISPATCHED";
    private static final String CODE_PAID_AT_BEFORE_PERIOD = "PAID_AT_BEFORE_PERIOD";
    private static final String CODE_PAID_AT_IN_FUTURE = "PAID_AT_IN_FUTURE";
    private static final int FIRST_SYSTEM_REFUND_SEQUENCE = 1;

    private final MemberService memberService;
    private final MembershipBillingService membershipBillingService;
    private final MembershipPaymentRepository payments;
    private final MembershipPaymentAttemptRepository attempts;
    private final MembershipRefundRepository refunds;
    private final BillingKeyRepository billingKeys;
    private final MembershipBillingProperties properties;
    private final MembershipBillingGate gate;
    private final MembershipRenewalRules rules;
    private final Clock clock;

    // 판정 순서: ① 잠금(회차의 시도 전부) ② 잠금 뒤 시각 ③ 회차 전체 강한 차단 ④ 오래된 관측 ⑤ 미발송 시도의 PG 거래 관측
    // ⑥ 확정 시도의 재전송·늦은 수납 ⑦ 미해결 시도의 정상 판정(확정 회차에 도착한 다른 시도의 수납 포함).
    // observedDispatchedAt은 호출자가 GET 전에 본 선점 시각이다 — 그 사이 다른 선점으로 바뀌었으면 이 관측으로 미확정 결과를 적지 않는다
    @Transactional
    public MembershipChargeOutcome apply(Long memberId, Long attemptId, LocalDateTime observedDispatchedAt,
                                         MembershipPgVerdict verdict, MembershipReconciliationTrigger trigger) {
        Locked locked = lock(memberId, attemptId);
        LocalDateTime now = LocalDateTime.now(clock);
        MembershipPayment payment = locked.payment();
        MembershipPaymentAttempt attempt = locked.attempt();
        if (locked.attempts().stream().anyMatch(MembershipPaymentAttempt::isSettlementBlocked)) {
            // 이 시도든 같은 회차의 다른 시도든 강한 차단이면 결과를 반영하지 않는다 — 다른 시도로 차단을 우회하지 않게
            log.warn("강한 정합성 차단 회차의 결과 반영 요청을 건너뜀(운영 확인 대상): attemptId={}, membershipPaymentId={}",
                attemptId, payment.getId());
            return MembershipChargeOutcome.RECONCILE_HALTED;
        }
        if (isStaleObservation(attempt, observedDispatchedAt) && !verdict.isTrustedPaid()) {
            // GET 전에 본 선점과 지금의 선점이 다르다 — 다른 선점자의 결과를 이 관측으로 덮지 않는다
            return MembershipChargeOutcome.IN_PROGRESS;
        }
        if (!attempt.isDispatched()) {
            return undispatchedOutcome(attempt, verdict, now);
        }
        if (attempt.isFinal()) {
            return finalAttemptOutcome(locked, verdict, now);
        }
        if (payment.isRenewal()) {
            return switch (verdict.kind()) {
                case PAID -> applyRenewalPaid(locked, verdict, now, false);
                case PAID_AMOUNT_MISMATCH -> applyRenewalPaid(locked, verdict, now, true);
                case FAILED -> applyRenewalFailed(locked, verdict, now);
                case UNRESOLVED, NOT_FOUND -> keepUnknown(locked, verdict);
                case UNTRUSTED -> manualReview(locked, verdict, now);
            };
        }
        return switch (verdict.kind()) {
            case PAID -> applyPaid(locked, verdict, now);
            case PAID_AMOUNT_MISMATCH -> applyAmountMismatch(locked, verdict, now);
            case FAILED -> applyFailed(locked, verdict, now);
            case UNRESOLVED, NOT_FOUND -> keepUnknown(locked, verdict);
            case UNTRUSTED -> manualReview(locked, verdict, now);
        };
    }

    // 미해결 발송 상태(DISPATCHING·UNKNOWN)인데 GET 전에 본 선점 시각과 지금 저장된 선점 시각이 다르다(µs 비교)
    private static boolean isStaleObservation(MembershipPaymentAttempt attempt, LocalDateTime observedDispatchedAt) {
        if (attempt.getStatus() != MembershipPaymentAttemptStatus.DISPATCHING
            && attempt.getStatus() != MembershipPaymentAttemptStatus.UNKNOWN) {
            return false;
        }
        if (observedDispatchedAt == null || attempt.getDispatchedAt() == null) {
            return observedDispatchedAt != attempt.getDispatchedAt();
        }
        return !observedDispatchedAt.truncatedTo(ChronoUnit.MICROS)
            .equals(attempt.getDispatchedAt().truncatedTo(ChronoUnit.MICROS));
    }

    // PG에 보낸 적 없는 시도(dispatchedAt 없음 — READY·미발송 CANCELED, 비정상 데이터의 FAILED·PAID 포함).
    // PG가 이 결제 ID의 거래 객체를 돌려줬다면 미발송 보장과 충돌한다 — 수납이면 UNDISPATCHED_PAID, 수납이 아닌 거래(실패·진행 중·취소·
    // 신뢰 불가 응답)면 UNDISPATCHED_PG_ACTIVITY로 강하게 차단한다. 자동 환불·이용권 반영은 하지 않고 수납 금액도 추정하지 않는다.
    // 404면 PG에 거래가 없음이 확인된 것이라 미발송과 일치한다. 조회 장애·timeout은 거래의 존재도 부재도 증명하지 못해 아무것도 바꾸지 않고
    // 재시도 가능한 결과를 돌려준다(웹훅은 다시 조회한다)
    private MembershipChargeOutcome undispatchedOutcome(MembershipPaymentAttempt attempt, MembershipPgVerdict verdict,
                                                        LocalDateTime now) {
        if (verdict.isTrustedPaid()) {
            attempt.blockSettlement(now, MembershipOpsReviewReason.UNDISPATCHED_PAID);
            log.error("PG에 보낸 적 없는 시도의 결제 ID로 수납이 있음 → 강한 차단(운영 확인): attemptId={}, status={}",
                attempt.getId(), attempt.getStatus());
            return MembershipChargeOutcome.RECONCILE_HALTED;
        }
        if (verdict.pgRecordObserved()) {
            attempt.blockSettlement(now, MembershipOpsReviewReason.UNDISPATCHED_PG_ACTIVITY);
            log.error("PG에 보낸 적 없는 시도의 결제 ID로 PG 거래 기록이 있음 → 강한 차단(운영 확인): attemptId={}, status={}, "
                + "verdict={}, code={}", attempt.getId(), attempt.getStatus(), verdict.kind(), verdict.code());
            return MembershipChargeOutcome.RECONCILE_HALTED;
        }
        if (!verdict.confirmsNoPgRecord()) {
            return MembershipChargeOutcome.STILL_UNKNOWN;
        }
        return switch (attempt.getStatus()) {
            case READY -> MembershipChargeOutcome.NOT_DISPATCHED;
            case CANCELED, FAILED -> MembershipChargeOutcome.ALREADY_APPLIED;
            case PAID -> {
                // 발송 근거도 PG 거래도 없는데 DB에는 수납으로 남아 있다
                attempt.blockSettlement(now, MembershipOpsReviewReason.APPLIED_STATE_INCONSISTENT);
                log.error("발송 근거 없는 PAID 시도이고 PG에도 거래가 없음 → 강한 차단(운영 확인): attemptId={}", attempt.getId());
                yield MembershipChargeOutcome.RECONCILE_HALTED;
            }
            default -> throw new IllegalStateException("발송 시각 없는 발송 상태 시도: " + attempt);
        };
    }

    // PG에 보낸 확정 시도(PAID·FAILED)에 결과가 다시 온 경우.
    // PAID: 정상 반영 PAID의 같은 근거 재전송은 회차의 반영 근거(회차 PAID·반영 시각·결제 시각)까지 맞을 때만 멱등이다 — 맞지 않으면
    // APPLIED_STATE_INCONSISTENT 차단(다시 반영하거나 환불을 새로 만들지 않는다). 환불 필요 PAID의 같은 근거 재전송은 SYSTEM 환불 행을
    // 확인·보충만 한다. 근거(시각·금액)가 다르거나 PG가 FAILED라고 하면 PAID_EVIDENCE_CONFLICT 차단(PAID를 내리지 않는다).
    // FAILED: 신뢰 가능한 PAID가 늦게 오면 수납 정정 — 상태 PAID·환불 필요·실제 수납액 SYSTEM 환불(이용권 반영 없음).
    // 404·조회 장애는 아무것도 증명하지 못해 재시도 결과를, 신뢰 불가·취소 상태는 운영 확인을 돌려준다 — 상태·이력은 바꾸지 않는다
    private MembershipChargeOutcome finalAttemptOutcome(Locked locked, MembershipPgVerdict verdict,
                                                        LocalDateTime now) {
        MembershipPaymentAttempt attempt = locked.attempt();
        if (verdict.kind() == MembershipPgVerdict.Kind.UNRESOLVED
            || verdict.kind() == MembershipPgVerdict.Kind.NOT_FOUND) {
            // 끝난 사실을 재확인한 것이 아니다. 상태·이력을 바꾸지 않고 재시도 가능한 결과를 돌려준다(웹훅은 다시 조회한다)
            return MembershipChargeOutcome.STILL_UNKNOWN;
        }
        if (verdict.kind() == MembershipPgVerdict.Kind.UNTRUSTED) {
            log.warn("확정 시도 재조회 결과를 신뢰할 수 없음: attemptId={}, code={}", attempt.getId(), verdict.code());
            return MembershipChargeOutcome.MANUAL_REVIEW;
        }
        if (attempt.getStatus() == MembershipPaymentAttemptStatus.FAILED) {
            if (!verdict.isTrustedPaid()) {
                return MembershipChargeOutcome.ALREADY_APPLIED;
            }
            if (locked.payment().isRenewal()) {
                // 회차 기간 전·미래 시각의 수납은 정정하지 않는다 — 어느 기간의 돈인지 알 수 없어 자동 환불도 만들지 않는다
                RenewalPeriod period = MembershipRenewalRules.storedPeriod(locked.payment());
                String invalidTimeCode = null;
                if (verdict.pgPaidAt().isBefore(period.previousEndAt())) {
                    invalidTimeCode = CODE_PAID_AT_BEFORE_PERIOD;
                } else if (verdict.pgPaidAt().isAfter(now)) {
                    invalidTimeCode = CODE_PAID_AT_IN_FUTURE;
                }
                if (invalidTimeCode != null) {
                    attempt.blockSettlement(now, MembershipOpsReviewReason.PAID_AT_INVALID);
                    log.error("늦은 수납의 결제 시각이 유효하지 않음: attemptId={}, code={}", attempt.getId(), invalidTimeCode);
                    return MembershipChargeOutcome.RECONCILE_HALTED;
                }
            }
            attempt.recordUnexpectedPaidForRefund(verdict.pgPaidAt(), now);
            SystemRefundEnsureResult refundResult = ensureSystemRefundRequested(attempt, verdict.paidAmount(),
                MembershipOpsReviewReason.LATE_PAID_AFTER_FAILED, now);
            if (refundResult == SystemRefundEnsureResult.BLOCKED) {
                return MembershipChargeOutcome.RECONCILE_HALTED;
            }
            log.error("실패로 확정한 시도에 늦은 수납 → 수납 정정·SYSTEM 환불 요청: attemptId={}, membershipPaymentId={}, amount={}",
                attempt.getId(), locked.payment().getId(), verdict.paidAmount());
            if (locked.payment().isRenewal() && locked.payment().isUnresolved()) {
                // 환불 대상 수납만 남은 회차는 재청구하지 않고 닫는다. 다른 시도가 살아 있으면 그대로 둔다
                MembershipRenewalDecision decision = rules.continueExisting(locked.memberId(), locked.membership(),
                    locked.payment(), locked.attempts(), now, Mode.SETTLE);
                if (decision.kind() == MembershipRenewalDecision.Kind.HALTED) {
                    return MembershipChargeOutcome.RECONCILE_HALTED;
                }
            }
            return MembershipChargeOutcome.UNAPPLIED_REFUND_REQUESTED;
        }
        if (attempt.getStatus() != MembershipPaymentAttemptStatus.PAID) {
            return MembershipChargeOutcome.ALREADY_APPLIED;
        }
        if (verdict.kind() == MembershipPgVerdict.Kind.FAILED) {
            return blockEvidenceConflict(attempt, verdict, now);
        }
        if (!verdict.isTrustedPaid()) {
            return MembershipChargeOutcome.ALREADY_APPLIED;
        }
        if (!sameEvidence(attempt, verdict, locked.payment())) {
            return blockEvidenceConflict(attempt, verdict, now);
        }
        if (attempt.getRefundRequiredAt() != null) {
            SystemRefundEnsureResult refundResult = ensureSystemRefundRequested(attempt, verdict.paidAmount(),
                refundReasonOf(attempt), now);
            if (refundResult == SystemRefundEnsureResult.BLOCKED) {
                return MembershipChargeOutcome.RECONCILE_HALTED;
            }
            return MembershipChargeOutcome.ALREADY_APPLIED;
        }
        if (!appliedBy(locked.payment(), attempt)) {
            attempt.blockSettlement(now, MembershipOpsReviewReason.APPLIED_STATE_INCONSISTENT);
            log.error("반영된 수납으로 남은 시도인데 회차 반영 근거가 맞지 않음 → 강한 차단(운영 확인): attemptId={}, "
                    + "membershipPaymentId={}, paymentStatus={}", attempt.getId(), locked.payment().getId(),
                locked.payment().getStatus());
            return MembershipChargeOutcome.RECONCILE_HALTED;
        }
        return MembershipChargeOutcome.ALREADY_APPLIED;
    }

    private static MembershipChargeOutcome blockEvidenceConflict(MembershipPaymentAttempt attempt,
                                                                 MembershipPgVerdict verdict, LocalDateTime now) {
        attempt.blockSettlement(now, MembershipOpsReviewReason.PAID_EVIDENCE_CONFLICT);
        log.error("저장된 수납 근거와 PG 재조회가 다름 → 강한 차단(운영 확인): attemptId={}, verdict={}", attempt.getId(),
            verdict.kind());
        return MembershipChargeOutcome.RECONCILE_HALTED;
    }

    // 같은 수납인지: 결제 시각(µs)과 금액. 금액 기준은 반영된 수납이면 회차 금액, 환불 필요 수납이면 그 시도의 SYSTEM 환불 금액
    // (환불 행이 없으면 — 원자성이 깨진 데이터 — 회차 금액)
    private boolean sameEvidence(MembershipPaymentAttempt attempt, MembershipPgVerdict verdict,
                                 MembershipPayment payment) {
        if (attempt.getPgPaidAt() == null || !attempt.getPgPaidAt().truncatedTo(ChronoUnit.MICROS)
            .equals(verdict.pgPaidAt().truncatedTo(ChronoUnit.MICROS))) {
            return false;
        }
        if (attempt.getRefundRequiredAt() == null) {
            return verdict.paidAmount().equals(payment.getAmount());
        }
        List<MembershipRefund> rows = systemRefundsOf(attempt);
        if (rows.isEmpty()) {
            return verdict.paidAmount().equals(payment.getAmount());
        }
        return rows.stream().allMatch(r -> r.getAmount().equals(verdict.paidAmount()));
    }

    private List<MembershipRefund> systemRefundsOf(MembershipPaymentAttempt attempt) {
        return refunds.findByMembershipPaymentAttemptIdAndRequestedByOrderByIdAsc(attempt.getId(), RefundRequester.SYSTEM);
    }

    // 정상 반영 PAID의 근거: 회차가 PAID이고 이용권 반영 시각이 있고 회차의 결제 시각이 이 시도의 결제 시각과 같다
    private static boolean appliedBy(MembershipPayment payment, MembershipPaymentAttempt attempt) {
        return payment.getStatus() == MembershipPaymentStatus.PAID && payment.isApplied()
            && payment.getPaidAt() != null
            && payment.getPaidAt().truncatedTo(ChronoUnit.MICROS)
            .equals(attempt.getPgPaidAt().truncatedTo(ChronoUnit.MICROS));
    }

    // 환불 행이 빠져 다시 만들 때의 사유: 시도에 남은 환불 필요 사유. 없거나 다른 종류면 확정 회차 수납으로 본다
    private static MembershipOpsReviewReason refundReasonOf(MembershipPaymentAttempt attempt) {
        MembershipOpsReviewReason reason = attempt.getOpsReviewReason();
        return reason == null || reason.isBlocking() ? MembershipOpsReviewReason.PAID_ON_CLOSED_PAYMENT : reason;
    }

    // READY 임대 만료. 같은 잠금 순서. 첫 결제는 넘겨받은 시각으로 판정하고(기존 계약), 갱신은 잠금 뒤 시각으로 판정한다
    @Transactional
    public ExpiredReadyResult handleExpiredReady(Long memberId, Long attemptId, LocalDateTime now) {
        Locked locked = lock(memberId, attemptId);
        if (locked.attempts().stream().anyMatch(MembershipPaymentAttempt::isSettlementBlocked)) {
            // 조회 뒤 차단이 확정된 회차. 미발송 보장에 반하는 증거가 있으므로 READY를 CANCELED로 접지도 새 시도를 만들지도 않는다
            log.warn("강한 정합성 차단 회차의 READY 만료 처리를 건너뜀(운영 확인 대상): attemptId={}", attemptId);
            return new ExpiredReadyResult(MembershipChargeOutcome.RECONCILE_HALTED, null);
        }
        if (locked.payment().isRenewal()) {
            return handleExpiredRenewalReady(locked, LocalDateTime.now(clock));
        }
        MembershipPayment payment = locked.payment();
        MembershipPaymentAttempt attempt = locked.attempt();
        if (!attempt.cancelNotDispatched(now)) {
            return new ExpiredReadyResult(outcomeOfUncancelable(attempt, now), attempt.getId());
        }
        if (!payment.isUnresolved()) {
            // ⑦ 확정 회차. 다른 READY가 남아 있어도(비정상) 실행 대상으로 돌려주지 않는다 — claim()은 회차 상태를 보지 않으므로 여기서 막는다
            return new ExpiredReadyResult(MembershipChargeOutcome.ALREADY_APPLIED, null);
        }
        List<MembershipPaymentAttempt> all = locked.attempts().stream()
            .sorted(Comparator.comparingInt(MembershipPaymentAttempt::getAttemptNo)).toList();
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

    // 갱신 READY 만료: 이 시도를 미발송 CANCELED로 접은 뒤 공통 규칙으로 이어 간다(다른 만료 READY 취소 → 살아 있는 시도 →
    // 해지·마감·한도 종료 → PENDING 잔존을 재시도 대기로 정규화 → 재시도 시각·동의·플래그·가드·카드 → 새 READY)
    private ExpiredReadyResult handleExpiredRenewalReady(Locked locked, LocalDateTime now) {
        MembershipPaymentAttempt attempt = locked.attempt();
        if (!attempt.cancelNotDispatched(now)) {
            return new ExpiredReadyResult(outcomeOfUncancelable(attempt, now), attempt.getId());
        }
        MembershipRenewalDecision decision = rules.continueExisting(locked.memberId(), locked.membership(),
            locked.payment(), locked.attempts(), now, Mode.CHARGE);
        return switch (decision.kind()) {
            case CHARGE_READY -> new ExpiredReadyResult(MembershipChargeOutcome.RETRY_READY, decision.attemptId());
            case PG_PENDING -> new ExpiredReadyResult(MembershipChargeOutcome.STILL_UNKNOWN, null);
            case IN_PROGRESS -> new ExpiredReadyResult(MembershipChargeOutcome.IN_PROGRESS, null);
            case WAITING_PAYMENT_METHOD ->
                new ExpiredReadyResult(MembershipChargeOutcome.PAYMENT_METHOD_REQUIRED, null);
            case CLOSED -> new ExpiredReadyResult(MembershipChargeOutcome.RENEWAL_STOPPED, null);
            case ALREADY_RESOLVED -> new ExpiredReadyResult(MembershipChargeOutcome.ALREADY_APPLIED, null);
            case HALTED -> new ExpiredReadyResult(MembershipChargeOutcome.RECONCILE_HALTED, null);
            case WAITING_RETRY, BLOCKED, SKIPPED ->
                new ExpiredReadyResult(MembershipChargeOutcome.NOT_DISPATCHED, null);
        };
    }

    // 정합성 예외로 TX2가 롤백된 뒤 실행기가 여는 별도의 짧은 TX. MEMBER부터 같은 잠금 순서로 다시
    // 잠그고 최신 상태로 원인을 재검증한다 — 그 사이 다른 처리(웹훅·운영 조치)로 해결됐으면 아무것도 남기지 않는다.
    // 원인이 그대로면 상태·회차·이용권·환불은 바꾸지 않고 시도에 정합성 코드 + 강한 차단 표시만 남겨 자동 정합화를 멈춘다
    @Transactional
    public boolean flagInconsistent(Long memberId, Long attemptId, Kind kind, LocalDateTime now) {
        Locked locked = lock(memberId, attemptId);
        MembershipPayment payment = locked.payment();
        MembershipPaymentAttempt attempt = locked.attempt();
        if (attempt.getStatus() != MembershipPaymentAttemptStatus.DISPATCHING
            && attempt.getStatus() != MembershipPaymentAttemptStatus.UNKNOWN) {
            return false;
        }
        if (attempt.isSettlementBlocked() || !payment.isUnresolved() || payment.isApplied()
            || payment.isRenewal() != isRenewalKind(kind) || !invariantStillBroken(locked, kind)) {
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
            // 갱신: 정상 갱신 대상인데 이용권의 현재 기간이 회차의 직전 종료(T)와 여전히 맞지 않는다
            case RENEWAL_BASE_PERIOD_MISMATCH -> MembershipRenewalRules.renewable(locked.membership())
                && !MembershipRenewalRules.matches(MembershipRenewalRules.storedPeriod(payment), locked.membership());
            // 갱신: C-4a CONFLICT의 사유(유료 아님·미활성·종료)가 여전히 확인되지 않는다
            case UNEXPLAINED_RENEWAL_CONFLICT -> MembershipRenewalRules.renewable(locked.membership());
        };
    }

    // 첫 결제 종류는 첫 결제 회차에만, 갱신 종류는 갱신 회차에만 적용한다
    private static boolean isRenewalKind(Kind kind) {
        return kind == Kind.RENEWAL_BASE_PERIOD_MISMATCH || kind == Kind.UNEXPLAINED_RENEWAL_CONFLICT;
    }

    // ---------- 잠금 ----------

    // attempts: 회차의 시도 전부(PK 오름차순으로 잠금). 첫 결제·갱신 공통 — 같은 회차의 다른 시도를 보는 판정(차단·확정 회차)이 있다
    private record Locked(Long memberId, BillingMembershipData membership, MembershipPayment payment,
                          MembershipPaymentAttempt attempt, List<MembershipPaymentAttempt> attempts) {
    }

    // ① MEMBER → ② ID만 일반 조회 → ③ 이용권 PK(C-7, 소유 재검증) → ④ 회차 PK → ⑤ 회차의 시도 전부 PK 오름차순 → ⑥ 연결 검증.
    // 대상 시도만 먼저 잠그지 않는다 — 잠근 목록에서 대상을 고른다
    private Locked lock(Long memberId, Long attemptId) {
        memberService.lockActiveMember(memberId);
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
        return new Locked(memberId, membership, payment, target, all);
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
        return manualReview(locked, verdict.code(), now);
    }

    private MembershipChargeOutcome manualReview(Locked locked, String code, LocalDateTime now) {
        MembershipPaymentAttempt attempt = locked.attempt();
        MembershipPayment payment = locked.payment();
        log.error("신뢰할 수 없는 PG 결과 → 운영 확인: attemptId={}, code={}", attempt.getId(), code);
        if (attempt.getStatus() != MembershipPaymentAttemptStatus.MANUAL_REVIEW) {
            attempt.markManualReview(code, now);
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
        if (ensureSystemRefundRequested(attempt, verdict.paidAmount(), reason, now) == SystemRefundEnsureResult.BLOCKED) {
            return MembershipChargeOutcome.RECONCILE_HALTED;
        }
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
        if (ensureSystemRefundRequested(attempt, verdict.paidAmount(), MembershipOpsReviewReason.PAID_ON_CLOSED_PAYMENT,
            now) == SystemRefundEnsureResult.BLOCKED) {
            return MembershipChargeOutcome.RECONCILE_HALTED;
        }
        log.error("확정된 회차에 늦은 PAID → SYSTEM 환불 요청: membershipPaymentId={}, attemptId={}, amount={}",
            locked.payment().getId(), attempt.getId(), verdict.paidAmount());
        return MembershipChargeOutcome.UNAPPLIED_REFUND_REQUESTED;
    }

    private enum SystemRefundEnsureResult { CREATED, EXISTING, BLOCKED }

    // SYSTEM 환불 멱등(시도 PK 잠금 아래). 같은 시도의 SYSTEM 환불 행을 상태와 무관하게 본다 — 0건이면 실제 수납액으로 순번 1을 만들고,
    // 금액이 모두 같고 FAILED가 아닌 행이 1건 이하면 그대로 둔다(REQUESTED·FAILED·COMPLETED 어느 것이든 새 요청 없음 — FAILED 뒤 재집행은
    // 환불 실행 단계가 맡는다). 금액이 다르거나 진행·완료 행이 둘 이상이면 자동으로 고치지 않고 강한 차단만 남긴다(TX는 롤백하지 않는다)
    private SystemRefundEnsureResult ensureSystemRefundRequested(MembershipPaymentAttempt attempt, Long actualAmount,
                                                                 MembershipOpsReviewReason reason, LocalDateTime now) {
        if (actualAmount == null) {
            throw new IllegalStateException("실제 수납액 없이 SYSTEM 환불을 요청할 수 없음: attemptId=" + attempt.getId());
        }
        List<MembershipRefund> rows = systemRefundsOf(attempt);
        if (rows.isEmpty()) {
            refunds.save(MembershipRefund.systemRefundRequested(attempt, actualAmount, reason.name(),
                FIRST_SYSTEM_REFUND_SEQUENCE, now));
            return SystemRefundEnsureResult.CREATED;
        }
        boolean sameAmount = rows.stream().allMatch(r -> r.getAmount().equals(actualAmount));
        long notFailed = rows.stream().filter(r -> r.getStatus() != MembershipRefundStatus.FAILED).count();
        if (sameAmount && notFailed <= 1) {
            return SystemRefundEnsureResult.EXISTING;
        }
        attempt.blockSettlement(now, MembershipOpsReviewReason.PAID_EVIDENCE_CONFLICT);
        log.error("SYSTEM 환불 기록이 수납과 맞지 않음 → 강한 차단(운영 확인): attemptId={}, rows={}, amount={}", attempt.getId(),
            rows.size(), actualAmount);
        return SystemRefundEnsureResult.BLOCKED;
    }

    // ---------- 갱신 결과 ----------

    // 갱신 PAID(금액 불일치 포함). 위에서부터 처음 맞는 행으로 끝난다.
    // 확정 회차 → 그 시도의 수납만 환불(회차·이용권 불변, C-4b 없음) / PG 결제 시각이 회차 T보다 이르거나 지금보다 늦음 → 운영 확인
    // (자동 연장·추정 환불·시각 보정 없음) / 반영할 수 없는 이용권 → 미반영 환불(C-4b는 끌 수 있는 이용권일 때만) /
    // 이용권 기간이 회차 T와 다름 → 정합성 예외·롤백 / 금액 불일치 → 실제 수납액 환불 / pgPaidAt ≥ G → 마감 뒤 수납 환불 /
    // now ≥ E → 기간 종료 환불 / 그 외 C-4a(E, E + 3일) EXTENDED만 첫 성공으로 인정
    private MembershipChargeOutcome applyRenewalPaid(Locked locked, MembershipPgVerdict verdict, LocalDateTime now,
                                                     boolean amountMismatch) {
        MembershipPayment payment = locked.payment();
        MembershipPaymentAttempt attempt = locked.attempt();
        BillingMembershipData membership = locked.membership();
        if (!payment.isUnresolved()) {
            return refundOnClosedPayment(locked, verdict, now);
        }
        RenewalPeriod period = MembershipRenewalRules.storedPeriod(payment);
        LocalDateTime pgPaidAt = verdict.pgPaidAt();
        if (pgPaidAt.isBefore(period.previousEndAt())) {
            return manualReview(locked, CODE_PAID_AT_BEFORE_PERIOD, now);
        }
        if (pgPaidAt.isAfter(now)) {
            return manualReview(locked, CODE_PAID_AT_IN_FUTURE, now);
        }
        if (!MembershipRenewalRules.renewable(membership)) {
            return closeUnappliedRenewal(locked, verdict, now, MembershipOpsReviewReason.PAID_CONFLICT,
                MembershipOpsReviewReason.PAID_CONFLICT.name(), MembershipRenewalRules.stoppable(membership));
        }
        if (!MembershipRenewalRules.matches(period, membership)) {
            log.error("갱신 회차의 직전 종료와 이용권 기간이 맞지 않음 → 롤백: membershipPaymentId={}, attemptId={}",
                payment.getId(), attempt.getId());
            throw new MembershipSettlementInvariantException(Kind.RENEWAL_BASE_PERIOD_MISMATCH, payment.getId(),
                attempt.getId());
        }
        if (amountMismatch) {
            log.error("갱신 수납액 불일치: membershipPaymentId={}, expected={}, actual={}", payment.getId(),
                payment.getAmount(), verdict.paidAmount());
            return closeUnappliedRenewal(locked, verdict, now, MembershipOpsReviewReason.PAID_AMOUNT_MISMATCH,
                MembershipPgVerdict.CODE_AMOUNT_MISMATCH, true);
        }
        if (!pgPaidAt.isBefore(period.graceDeadlineAt())) {
            return closeUnappliedRenewal(locked, verdict, now, MembershipOpsReviewReason.LATE_PAID_AFTER_DEADLINE,
                MembershipOpsReviewReason.LATE_PAID_AFTER_DEADLINE.name(), true);
        }
        if (!now.isBefore(period.endAt())) {
            return closeUnappliedRenewal(locked, verdict, now, MembershipOpsReviewReason.RENEWAL_PERIOD_ENDED,
                MembershipOpsReviewReason.RENEWAL_PERIOD_ENDED.name(), true);
        }
        MembershipBillingResult result = membershipBillingService.applyRenewalPaid(payment.getMembershipId(),
            period.endAt(), period.graceEndAt());
        switch (result) {
            case EXTENDED -> {
                attempt.markPaid(pgPaidAt, now);
                payment.markPaid(pgPaidAt, now);
                if (!Duration.between(pgPaidAt, now).minus(properties.longDelay()).isNegative()) {
                    // 확인이 오래 걸린 정상 반영. 보상 판단만 운영에 넘긴다(자동 정합화 중단 표시가 아니다)
                    attempt.flagOpsReview(now, MembershipOpsReviewReason.RENEWAL_LONG_DELAY);
                }
                return MembershipChargeOutcome.EXTENDED;
            }
            case ALREADY_APPLIED -> {
                log.error("이용기간은 이미 연장됐는데 갱신 회차가 미반영(I-22 위반) → 롤백: membershipPaymentId={}, attemptId={}",
                    payment.getId(), attempt.getId());
                throw new MembershipSettlementInvariantException(Kind.RENEWAL_BASE_PERIOD_MISMATCH, payment.getId(),
                    attempt.getId());
            }
            case CONFLICT -> {
                log.error("사유를 확인할 수 없는 C-4a CONFLICT → 롤백: membershipPaymentId={}, attemptId={}",
                    payment.getId(), attempt.getId());
                throw new MembershipSettlementInvariantException(Kind.UNEXPLAINED_RENEWAL_CONFLICT, payment.getId(),
                    attempt.getId());
            }
            default -> throw new IllegalStateException("갱신 반영에서 올 수 없는 결과 " + result
                + ": membershipPaymentId=" + payment.getId());
        }
    }

    // 갱신 확정 실패. 이 시도를 FAILED로 적고(확정 시각 = now) 회차는 공통 규칙으로 이어 간다 — 다른 시도가 살아 있으면 그대로,
    // 해지·종료·마감·발송 한도면 회차 FAILED + C-4b, 아니면 이 실패 + 24시간에 재시도 대기(마감 이상이면 예약 없음).
    // 같은 실패의 재수신은 확정 시도 판정(finalAttemptOutcome)에서 끝나므로 예약 시각·시도 수가 다시 계산되지 않는다
    private MembershipChargeOutcome applyRenewalFailed(Locked locked, MembershipPgVerdict verdict, LocalDateTime now) {
        MembershipPayment payment = locked.payment();
        MembershipPaymentAttempt attempt = locked.attempt();
        attempt.markFailed(verdict.code(), now);
        if (!payment.isUnresolved()) {
            return MembershipChargeOutcome.ALREADY_APPLIED;
        }
        MembershipRenewalDecision decision = rules.afterFailure(locked.membership(), payment, locked.attempts(),
            attempt, now);
        return switch (decision.kind()) {
            case PG_PENDING -> MembershipChargeOutcome.STILL_UNKNOWN;
            case IN_PROGRESS -> MembershipChargeOutcome.IN_PROGRESS;
            case CLOSED -> MembershipChargeOutcome.RENEWAL_STOPPED;
            case WAITING_RETRY -> MembershipChargeOutcome.RETRY_WAITING;
            case HALTED -> MembershipChargeOutcome.RECONCILE_HALTED;
            default -> throw new IllegalStateException("갱신 실패 처리에서 올 수 없는 판정 " + decision.kind()
                + ": membershipPaymentId=" + payment.getId());
        };
    }

    // 반영하지 않는 갱신 수납: 시도 PAID + 환불 필요 + 회차 FAILED + (끌 수 있는 이용권이면 C-4b) + SYSTEM 환불 REQUESTED를 한 TX에.
    // 비유료·미활성 이용권에는 C-4b를 부르지 않는다 — CONFLICT로 환불 요청까지 롤백되지 않게
    private MembershipChargeOutcome closeUnappliedRenewal(Locked locked, MembershipPgVerdict verdict,
                                                          LocalDateTime now, MembershipOpsReviewReason reason,
                                                          String failureReason, boolean stopRenewal) {
        MembershipPayment payment = locked.payment();
        MembershipPaymentAttempt attempt = locked.attempt();
        attempt.markPaid(verdict.pgPaidAt(), now);
        attempt.requireRefund(now, reason);
        payment.closeUnapplied(failureReason);
        if (stopRenewal) {
            rules.stopRenewal(payment.getMembershipId());
        }
        if (ensureSystemRefundRequested(attempt, verdict.paidAmount(), reason, now) == SystemRefundEnsureResult.BLOCKED) {
            return MembershipChargeOutcome.RECONCILE_HALTED;
        }
        log.warn("갱신 PAID를 반영하지 않음 → SYSTEM 환불 요청: membershipPaymentId={}, reason={}, amount={}",
            payment.getId(), reason, verdict.paidAmount());
        return MembershipChargeOutcome.UNAPPLIED_REFUND_REQUESTED;
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
