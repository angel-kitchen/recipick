package com.recipick.domain.payment.facade;

import com.recipick.domain.member.dto.MemberProfile;
import com.recipick.domain.member.service.MemberService;
import com.recipick.domain.payment.config.MembershipBillingGate;
import com.recipick.domain.payment.config.MembershipBillingProperties;
import com.recipick.domain.payment.entity.enums.MembershipOpsReviewReason;
import com.recipick.domain.payment.exception.MembershipSettlementInvariantException;
import com.recipick.domain.payment.exception.PaymentErrorCode;
import com.recipick.domain.payment.repository.MembershipPaymentAttemptRepository;
import com.recipick.domain.payment.service.MembershipChargeApplyDecision;
import com.recipick.domain.payment.service.MembershipChargeClaim;
import com.recipick.domain.payment.service.MembershipChargeOutcome;
import com.recipick.domain.payment.service.MembershipChargeTicket;
import com.recipick.domain.payment.service.MembershipPaymentAttemptRef;
import com.recipick.domain.payment.service.MembershipPgVerdict;
import com.recipick.domain.payment.service.MembershipReconciliationResult;
import com.recipick.domain.payment.service.MembershipReconciliationTrigger;
import com.recipick.domain.payment.service.MembershipRefundExecutionResult;
import com.recipick.domain.payment.service.PgObservation;
import com.recipick.global.exception.BusinessException;
import com.recipick.infra.portone.PortOneBillingClient;
import com.recipick.infra.portone.PortOnePaymentClient;
import com.recipick.infra.portone.dto.PortOneBillingPaymentOutcome;
import com.recipick.infra.portone.dto.PortOneBillingPaymentRequest;
import com.recipick.infra.portone.dto.PortOneBillingPaymentResult;
import com.recipick.infra.portone.dto.PortOnePayment;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

// 공용 실행기. PG는 잠금 밖에서 부른다 — 진입점은 Propagation.NEVER로 바깥 트랜잭션 안에서의 호출을 PG 요청 전에
// 거절한다(바깥 TX가 있으면 선점·TX2가 그 TX에 참여해 커밋 전에 PG를 부르게 된다. REQUIRES_NEW로 우회하지 않는다).
// execute: 플래그(선점 전) → 회원 프로필(선점 전, 실패하면 READY 그대로) → 선점 TX → 플래그·접수 유효 창·회차 차단(POST 직전, 꺼졌거나
//          창이 끝났거나 차단됐으면 미발송 되돌림; 창이 끝났으면 임대 만료 규칙으로 회차 정리) → PG POST → 같은 결제 ID GET → TX2.
//          POST 응답은 참고일 뿐 확정은 GET으로만 한다.
// reconcile: 플래그와 무관하게 GET → TX2만. 재조회 원인(복구 Sweep·검증된 웹훅·운영자 재조회)에 따라 GET을 허용하는 시도 상태가 다르다 —
//          복구는 READY(미발송)·신선한 DISPATCHING(다른 Worker가 PG 호출 중)·MANUAL_REVIEW·확정 시도를 조회하지 않고, 웹훅·운영자는
//          회차가 차단되지 않았으면 모두 조회한다. 차단된 회차는 복구·웹훅이 GET하지 않고, 운영자 재조회만 GET 뒤 TX2 없이 관측값을 돌려준다.
// 선점에 실패했는데 잠금 아래에서 본 그 시도가 임대 안의 DISPATCHING이면 다른 Worker가 PG 호출 중이다 → GET 없이 IN_PROGRESS.
// 어떤 경우에도 새 결제 ID로 다시 POST하지 않는다. 4단계 갱신·재시도도 같은 메서드를 쓴다.
// TX2가 명시적 정합성 오류로 롤백되면 여기서 잡아 별도 TX로 원인을 재검증한 뒤 강한 차단 표시를 남기고 RECONCILE_HALTED를 돌려준다 —
// 복구·웹훅·운영자 어느 진입점도 이 예외를 따로 다루지 않는다.
// GET 결과는 판정과 PG 응답을 함께 보관한다(PgObservation). TX2가 "수납 PAID 시도 + 취소 계열 응답"으로 환불 위임을 결정하면 커밋 뒤
// 같은 응답을 환불 정합화에 넘긴다(추가 GET·취소 POST 없음) — 자기 POST 뒤 GET과 재조회 두 경로가 같은 처리를 탄다.
// TX2가 SYSTEM 환불을 요청(또는 그 존재를 재확인)하면 커밋 뒤 TX 밖에서 환불 실행을 큐에 예약한다(예약 실패는 로그만 — DB 복구가 받는다).
// 로그에는 회원·시도 ID·오류 코드만. 프로필(이름·전화·이메일)·빌링키·PG 예외 객체는 넣지 않는다
@Slf4j
@Component
@RequiredArgsConstructor
public class MembershipChargeExecutor {

    private final MembershipBillingGate gate;
    private final MembershipBillingProperties properties;
    private final MemberService memberService;
    private final MembershipChargeDispatchProcessor dispatchProcessor;
    private final MembershipChargeResultProcessor resultProcessor;
    private final MembershipPaymentAttemptRepository attempts;
    private final PortOneBillingClient portOneBillingClient;
    private final PortOnePaymentClient portOnePaymentClient;
    private final BillingKeyReconciliationFacade billingKeyReconciliation;
    private final MembershipRefundExecutionFacade refundExecution;
    private final MembershipRefundRecoveryFacade refundRecovery;
    private final Clock clock;

    // READY 시도에서 시작한다. 플래그 OFF면 POST 없이 READY 유지(임대 만료 규칙이 정리)
    @Transactional(propagation = Propagation.NEVER)
    public MembershipChargeOutcome execute(Long memberId, Long attemptId) {
        if (!gate.newChargeEnabled()) {
            return MembershipChargeOutcome.NOT_DISPATCHED;
        }
        MemberProfile profile = profileForCharge(memberId, attemptId);
        if (profile == null) {
            return MembershipChargeOutcome.NOT_DISPATCHED;
        }
        MembershipChargeClaim claim = dispatchProcessor.claim(attemptId);
        if (!claim.isClaimed()) {
            return afterFailedClaim(memberId, attemptId, claim);
        }
        MembershipChargeTicket ticket = claim.ticket().orElseThrow();
        LocalDateTime now = LocalDateTime.now(clock);
        if (!gate.newChargeEnabled()) {
            // 선점과 POST 사이에 플래그가 꺼짐. POST를 보내지 않았으므로 READY로 되돌린다.
            // 되돌리지 못하면(다른 선점·상태 변경) 강제하지 않고 IN_PROGRESS — 기존 ID 정합화 경로가 맡는다
            return dispatchProcessor.releaseUnsent(ticket)
                ? MembershipChargeOutcome.NOT_DISPATCHED
                : MembershipChargeOutcome.IN_PROGRESS;
        }
        if (!now.isBefore(ticket.acceptUntil())) {
            // 접수 마감(첫 결제는 첫 시도 requestedAt + 30분, 갱신은 결제 마감 G)이 POST 직전에 지남. 보내지 않고 되돌린 뒤 임대 만료
            // 규칙으로 회차를 정리한다(새 시도의 임대는 마감을 넘지 않으므로 되돌린 READY는 이미 만료 상태다)
            log.info("접수 마감이 지나 청구하지 않음: attemptId={}, acceptUntil={}", attemptId, ticket.acceptUntil());
            if (!dispatchProcessor.releaseUnsent(ticket)) {
                return MembershipChargeOutcome.IN_PROGRESS;
            }
            return resultProcessor.handleExpiredReady(memberId, attemptId, LocalDateTime.now(clock)).outcome();
        }
        if (roundBlocked(ticket.membershipPaymentId())) {
            // 선점이 커밋된 뒤 같은 회차의 다른 시도에서 강한 차단이 확정됨. POST를 보내지 않고 되돌린다. 이 검사는 보조 방어다 —
            // PG 호출이 TX 밖이라 경합을 완전히 없애지 못하고, 그 경우 TX2가 회차 전체 차단을 다시 확인해 반영을 막는다
            log.warn("강한 정합성 차단 회차라 청구하지 않음(운영 확인 대상): attemptId={}, membershipPaymentId={}", attemptId,
                ticket.membershipPaymentId());
            return dispatchProcessor.releaseUnsent(ticket)
                ? MembershipChargeOutcome.RECONCILE_HALTED
                : MembershipChargeOutcome.IN_PROGRESS;
        }
        PortOneBillingPaymentResult response = portOneBillingClient.payWithBillingKey(ticket.portonePaymentId(),
            PortOneBillingPaymentRequest.of(ticket.billingKey(), ticket.amount(), String.valueOf(memberId),
                profile.name(), profile.email(), profile.phone()));
        log.info("빌링키 청구 호출 완료(재조회로 확정): attemptId={}, outcome={}", attemptId, response.outcome());
        // 여기부터는 플래그가 꺼져도 멈추지 않는다 — PG에 도착했을 수 있다
        try {
            PgObservation observation = recheck(ticket.portonePaymentId(), ticket.amount());
            return apply(memberId, attemptId, ticket.dispatchedAt(), observation,
                MembershipReconciliationTrigger.SCHEDULED);
        } finally {
            if (response.outcome() == PortOneBillingPaymentOutcome.BILLING_KEY_UNUSABLE) {
                // 결제 결과 확인이 실패해도 그 키만은 다시 본다. 키 확인 실패는 격리하고 결제 쪽 예외는 그대로 올라간다
                try {
                    billingKeyReconciliation.verifyUnusable(memberId, ticket.billingKeyId());
                } catch (RuntimeException e) {
                    log.warn("사용 불가 응답 뒤 빌링키 확인 실패: attemptId={}, cause={}", attemptId,
                        e.getClass().getSimpleName());
                }
            }
        }
    }

    // 복구 Sweep·선점 실패 뒤의 재조회(원인 SCHEDULED)
    @Transactional(propagation = Propagation.NEVER)
    public MembershipChargeOutcome reconcile(Long memberId, Long attemptId) {
        return reconcile(memberId, attemptId, MembershipReconciliationTrigger.SCHEDULED);
    }

    // 원인별 재조회. 결과만 필요한 호출자용(복구·웹훅)
    @Transactional(propagation = Propagation.NEVER)
    public MembershipChargeOutcome reconcile(Long memberId, Long attemptId, MembershipReconciliationTrigger trigger) {
        return reconcileInternal(memberId, attemptId, trigger).outcome();
    }

    // 운영자 재조회. 결과와 함께 이번 GET의 판정(관측값)을 돌려준다 — 차단된 회차도 GET은 하되 TX2는 부르지 않는다.
    // 호출자(운영 Facade)는 요청 접수 감사를 먼저 커밋한 뒤 부른다
    @Transactional(propagation = Propagation.NEVER)
    public MembershipReconciliationResult recheckForAdmin(Long memberId, Long attemptId) {
        return reconcileInternal(memberId, attemptId, MembershipReconciliationTrigger.ADMIN_RECHECK);
    }

    // 잠금 전 투영 값으로 GET 여부만 정한다. 상태 변경은 TX2가 잠금 아래에서 다시 판정한다. 같은 요청에서 GET은 한 번이다
    private MembershipReconciliationResult reconcileInternal(Long memberId, Long attemptId,
                                                             MembershipReconciliationTrigger trigger) {
        MembershipPaymentAttemptRef ref = attempts.findRef(attemptId)
            .orElseThrow(() -> new BusinessException(PaymentErrorCode.MEMBERSHIP_PAYMENT_NOT_FOUND));
        if (ref.isSettlementBlocked() || roundBlocked(ref.membershipPaymentId())) {
            if (trigger != MembershipReconciliationTrigger.ADMIN_RECHECK) {
                return MembershipReconciliationResult.withoutLookup(MembershipChargeOutcome.RECONCILE_HALTED);
            }
            // 운영자는 PortOne 상태를 봐야 조치할 수 있다. 조회만 하고 반영은 하지 않는다
            PgObservation observed = recheck(ref.portonePaymentId(), ref.amount());
            log.info("차단 회차의 운영자 재조회(반영 없음): attemptId={}, verdict={}", attemptId, observed.verdict().kind());
            return new MembershipReconciliationResult(MembershipChargeOutcome.RECONCILE_HALTED, observed.verdict());
        }
        if (trigger == MembershipReconciliationTrigger.SCHEDULED) {
            LocalDateTime now = LocalDateTime.now(clock);
            MembershipChargeOutcome skipped = switch (ref.status()) {
                case READY -> MembershipChargeOutcome.NOT_DISPATCHED;
                case MANUAL_REVIEW -> MembershipChargeOutcome.MANUAL_REVIEW;
                case DISPATCHING -> isStale(ref.dispatchedAt(), now) ? null : MembershipChargeOutcome.IN_PROGRESS;
                case UNKNOWN -> null;
                case PAID, FAILED, CANCELED -> MembershipChargeOutcome.ALREADY_APPLIED;
            };
            if (skipped != null) {
                return MembershipReconciliationResult.withoutLookup(skipped);
            }
        }
        PgObservation observation = recheck(ref.portonePaymentId(), ref.amount());
        MembershipChargeOutcome outcome = apply(memberId, attemptId, ref.dispatchedAt(), observation, trigger);
        return new MembershipReconciliationResult(outcome, observation.verdict());
    }

    // TX2 호출. 명시적 정합성 오류는 TX2가 롤백된 뒤 별도 TX로 원인을 재검증해 강한 차단 표시를 남긴다(실패해도 결과는 RECONCILE_HALTED —
    // 다음 재조회가 같은 길을 한 번 더 간다). 환불 위임 결정이면 커밋 뒤 같은 PG 응답으로 환불 정합화를 부른다.
    // SYSTEM 환불 요청(또는 재확인)이면 커밋 뒤 큐에 예약한다
    private MembershipChargeOutcome apply(Long memberId, Long attemptId, LocalDateTime observedDispatchedAt,
                                          PgObservation observation, MembershipReconciliationTrigger trigger) {
        MembershipChargeApplyDecision decision;
        try {
            decision = resultProcessor.decide(memberId, attemptId, observedDispatchedAt, observation, trigger);
        } catch (MembershipSettlementInvariantException e) {
            log.error("결과 반영 중 정합성 오류(롤백, 운영 정합화 대상): attemptId={}, membershipPaymentId={}, kind={}", attemptId,
                e.getMembershipPaymentId(), e.getKind());
            try {
                resultProcessor.flagInconsistent(memberId, attemptId, e.getKind(), LocalDateTime.now(clock));
            } catch (RuntimeException flagFailure) {
                log.warn("정합성 오류 시도의 운영 표시 실패(다음 재조회에서 재시도): attemptId={}, cause={}", attemptId,
                    flagFailure.getClass().getSimpleName());
            }
            return MembershipChargeOutcome.RECONCILE_HALTED;
        }
        if (decision.refundReconciliationRequired()) {
            MembershipRefundExecutionResult refundResult = refundExecution.reconcileObserved(memberId, attemptId,
                observation.payment());
            log.info("수납 PAID 시도의 취소 관측을 환불 정합화로 처리: attemptId={}, refundResult={}", attemptId, refundResult);
            // 환불 쪽 결과를 그대로 결제 결과로 옮긴다 — 미확정이면 웹훅·복구가 다시 보고, 환불 행 없는 취소·운영 확인·차단은
            // 처리 완료로 기록하지 않는다. SKIPPED는 토큰 없는 TX2에서 나오지 않으므로 나오면 운영 확인으로 보낸다
            return switch (refundResult) {
                case COMPLETED, FAILED -> MembershipChargeOutcome.REFUND_RECONCILED;
                case PENDING -> MembershipChargeOutcome.REFUND_RECONCILIATION_PENDING;
                case REVIEW_REQUIRED, SKIPPED -> MembershipChargeOutcome.REFUND_RECONCILIATION_REVIEW_REQUIRED;
            };
        }
        MembershipChargeOutcome outcome = decision.outcome();
        if (outcome == MembershipChargeOutcome.UNAPPLIED_REFUND_REQUESTED
            || outcome == MembershipChargeOutcome.ALREADY_APPLIED) {
            refundRecovery.scheduleRequestedOf(attemptId);
        }
        return outcome;
    }

    // 선점 실패 뒤 — 잠금 아래에서 본 상태로 정한다. 차단으로 거절됐으면 임대 만료가 아니라 차단이다. 다른 Worker가 임대 안에서
    // PG 호출 중이면 기다린다(GET 없음). 오래된 DISPATCHING·UNKNOWN만 재조회한다
    private MembershipChargeOutcome afterFailedClaim(Long memberId, Long attemptId, MembershipChargeClaim claim) {
        if (claim.observedStatus() == null) {
            throw new BusinessException(PaymentErrorCode.MEMBERSHIP_PAYMENT_NOT_FOUND);
        }
        if (claim.settlementBlocked()) {
            return MembershipChargeOutcome.RECONCILE_HALTED;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        return switch (claim.observedStatus()) {
            case READY -> MembershipChargeOutcome.NOT_DISPATCHED;           // 임대 만료 — 5-4가 정리
            case DISPATCHING -> isStale(claim.observedDispatchedAt(), now)
                ? reconcile(memberId, attemptId)
                : MembershipChargeOutcome.IN_PROGRESS;
            case UNKNOWN -> reconcile(memberId, attemptId);
            case MANUAL_REVIEW -> MembershipChargeOutcome.MANUAL_REVIEW;
            default -> MembershipChargeOutcome.ALREADY_APPLIED;             // PAID·FAILED·CANCELED
        };
    }

    // 회차에 강한 정합성 차단 시도가 있는지(일반 조회, 잠금 없음)
    private boolean roundBlocked(Long membershipPaymentId) {
        return attempts.existsByMembershipPaymentIdAndOpsReviewReasonIn(membershipPaymentId,
            MembershipOpsReviewReason.BLOCKING);
    }

    // DISPATCHING이 L + M보다 오래됐으면 그 선점자는 끝났다고 본다(복구와 같은 기준)
    private boolean isStale(LocalDateTime dispatchedAt, LocalDateTime now) {
        return dispatchedAt == null
            || !now.isBefore(dispatchedAt.plus(properties.lease()).plus(properties.dispatchMargin()));
    }

    // 프로필은 선점 전에, TX 밖에서. 실패하면 PG를 부르지 않고 시도는 READY로 남긴다(UNKNOWN 기록 없음)
    private MemberProfile profileForCharge(Long memberId, Long attemptId) {
        MemberProfile profile;
        try {
            profile = memberService.getProfile(memberId);
        } catch (BusinessException e) {
            log.warn("결제용 회원 정보를 읽지 못해 청구하지 않음: memberId={}, attemptId={}, code={}", memberId, attemptId,
                e.getErrorCode().getCode());
            return null;
        }
        if (!StringUtils.hasText(profile.name()) || !StringUtils.hasText(profile.phone())
            || !StringUtils.hasText(profile.email())) {
            log.warn("PG 고객 정보가 비어 있어 청구하지 않음: memberId={}, attemptId={}", memberId, attemptId);
            return null;
        }
        return profile;
    }

    // 같은 결제 ID 재조회. 404는 NOT_FOUND(UNKNOWN 유지), 그 밖의 조회 오류는 코드만 남긴 UNRESOLVED. 예외 객체는 넘기지 않는다.
    // 응답 객체는 판정과 함께 보관한다(TX2의 환불 위임 결정에 쓴다)
    private PgObservation recheck(String portonePaymentId, long amount) {
        try {
            PortOnePayment payment = portOnePaymentClient.getPayment(portonePaymentId);
            return new PgObservation(MembershipPgVerdict.of(portonePaymentId, amount, payment), payment);
        } catch (BusinessException e) {
            if (e.getErrorCode() == PaymentErrorCode.PAYMENT_NOT_FOUND) {
                return PgObservation.withoutPayment(MembershipPgVerdict.notFound());
            }
            return PgObservation.withoutPayment(MembershipPgVerdict.unresolved(e.getErrorCode().getCode()));
        }
    }
}
