package com.recipick.domain.payment.facade;

import com.recipick.domain.payment.entity.enums.MembershipRefundOpsReason;
import com.recipick.domain.payment.entity.enums.RefundRequester;
import com.recipick.domain.payment.exception.PaymentErrorCode;
import com.recipick.domain.payment.facade.MembershipRefundExecutionProcessor.Claim;
import com.recipick.domain.member.service.MembershipBillingService;
import com.recipick.domain.payment.repository.MembershipPaymentAttemptRepository;
import com.recipick.domain.payment.repository.MembershipRefundRepository;
import com.recipick.domain.payment.service.MembershipPaymentAttemptRef;
import com.recipick.domain.payment.service.MembershipRefundApplyResult;
import com.recipick.domain.payment.service.MembershipRefundApplyResult.Signal;
import com.recipick.domain.payment.service.MembershipRefundCancellationMatcher;
import com.recipick.domain.payment.service.MembershipRefundCancellationMatcher.Match;
import com.recipick.domain.payment.service.MembershipRefundCheckResult;
import com.recipick.domain.payment.service.MembershipRefundExecutionResult;
import com.recipick.domain.payment.service.MembershipRefundRef;
import com.recipick.domain.payment.service.MembershipRefundTicket;
import com.recipick.domain.payment.service.RefundCancellationBinding;
import com.recipick.domain.payment.service.RefundMatchSnapshot;
import com.recipick.domain.payment.service.RefundPgObservation;
import com.recipick.global.exception.BusinessException;
import com.recipick.infra.portone.PortOneMembershipRefundClient;
import com.recipick.infra.portone.PortOnePaymentClient;
import com.recipick.infra.portone.PortOneProperties;
import com.recipick.infra.portone.dto.PortOneMembershipCancelRequest;
import com.recipick.infra.portone.dto.PortOneMembershipCancelResult;
import com.recipick.infra.portone.dto.PortOnePayment;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// 환불 실행기. PG는 잠금 밖에서 부른다 — 진입점은 Propagation.NEVER로 바깥 트랜잭션 안에서의 호출을 거절한다.
// execute: 실행권 TX → GET → 결과 반영 TX(TX2) → [POST 허용이면 POST 준비 TX → 취소 POST → 응답 ID 기록 TX → GET → TX2] → 예약·종료.
//          최종 COMPLETED·FAILED는 POST 응답이 아니라 GET 결과로만 확정한다. 같은 환불 행의 멱등 키·본문은 바뀌지 않는다.
// recheck: 관리자 재조회. 실행권·토큰 없이 GET 1회 → TX2(강한 차단 회차는 관측만) → 관측값을 결과와 함께 돌려준다. POST 없음.
// reconcileObserved: 다른 경로(결제 재조회·취소 웹훅)가 이미 받은 GET 결과를 그 시도의 환불들에 반영한다. GET·POST 없음.
// 결과 확인 창(24시간)과 재조회 간격은 모든 미확정 경로에 같이 적용한다 — 기준은 최초 발송 시각, 없으면 접수 시각.
// 재POST는 최초 발송 + 2시간 55분 미만에서만 같은 키로 보낸다(공식 멱등 보장 3시간 안). 그 뒤에는 GET 정합화만 계속한다
@Slf4j
@Component
@RequiredArgsConstructor
public class MembershipRefundExecutionFacade {

    public static final Duration REPOST_WINDOW = Duration.ofHours(2).plusMinutes(55);
    public static final Duration UNKNOWN_WINDOW = Duration.ofHours(24);
    private static final Duration DELAY_MIN = Duration.ofMinutes(1);
    private static final Duration DELAY_MAX = Duration.ofMinutes(10);

    private final MembershipRefundExecutionProcessor executionProcessor;
    private final MembershipRefundResultProcessor resultProcessor;
    private final MembershipRefundRepository refunds;
    private final MembershipPaymentAttemptRepository attempts;
    private final MembershipBillingService membershipBillingService;
    private final PortOnePaymentClient portOnePaymentClient;
    private final PortOneMembershipRefundClient refundClient;
    private final PortOneProperties portOneProperties;
    private final Clock clock;

    @Transactional(propagation = Propagation.NEVER)
    public MembershipRefundExecutionResult execute(Long refundId) {
        Claim claim = executionProcessor.claim(refundId);
        Optional<MembershipRefundTicket> claimedTicket = claim.ticket();
        if (claimedTicket.isEmpty()) {
            return claim.halted() ? MembershipRefundExecutionResult.REVIEW_REQUIRED
                : MembershipRefundExecutionResult.SKIPPED;
        }
        MembershipRefundTicket ticket = claimedTicket.get();
        Lookup lookup = lookup(ticket.portonePaymentId());
        if (lookup.payment() == null) {
            return nextCheckOrExpire(ticket, "GET_" + lookup.code());
        }
        MembershipRefundApplyResult applied = resultProcessor.apply(ticket.memberId(), ticket.attemptId(), refundId,
            lookup.payment());
        if (!applied.postAllowed()) {
            return settle(ticket, applied);
        }
        return dispatch(ticket);
    }

    // 관리자 재조회. 호출자(운영 Facade)는 감사를 먼저 커밋한 뒤 부른다
    @Transactional(propagation = Propagation.NEVER)
    public MembershipRefundCheckResult recheck(Long refundId) {
        MembershipRefundRef ref = refunds.findRef(refundId)
            .orElseThrow(() -> new BusinessException(PaymentErrorCode.MEMBERSHIP_REFUND_NOT_FOUND));
        Lookup lookup = lookup(ref.portonePaymentId());
        if (lookup.payment() == null) {
            return new MembershipRefundCheckResult(MembershipRefundExecutionResult.PENDING,
                RefundPgObservation.lookupFailed(lookup.code()));
        }
        RefundPgObservation observation = observe(ref, lookup.payment());
        if (ref.isSettlementBlocked()) {
            // 강한 차단 상태에서는 관측만 돌려준다 — 재조회가 성공했다고 자동 반영·차단 해제하지 않는다
            log.info("강한 차단 환불의 관리자 재조회(반영 없음): refundId={}", refundId);
            return new MembershipRefundCheckResult(MembershipRefundExecutionResult.REVIEW_REQUIRED, observation);
        }
        Long memberId = membershipBillingService.findMemberIdOfMembership(ref.membershipId());
        MembershipRefundApplyResult applied = resultProcessor.apply(memberId, ref.attemptId(), refundId,
            lookup.payment());
        return new MembershipRefundCheckResult(outcomeWithoutToken(applied), observation);
    }

    // 결제 재조회(수납 PAID 시도에 취소 계열 관측)·취소 웹훅이 받은 GET 결과를 그 시도의 환불들에 반영한다. 추가 GET·POST 없음.
    // 회원 ID는 TX 밖에서 역조회한다(TX2의 첫 DB 작업이 회원 잠금이어야 한다)
    @Transactional(propagation = Propagation.NEVER)
    public MembershipRefundExecutionResult reconcileObserved(Long attemptId, PortOnePayment observed) {
        MembershipPaymentAttemptRef attempt = attempts.findRef(attemptId)
            .orElseThrow(() -> new BusinessException(PaymentErrorCode.MEMBERSHIP_PAYMENT_NOT_FOUND));
        Long memberId = membershipBillingService.findMemberIdOfMembership(attempt.membershipId());
        return reconcileObserved(memberId, attemptId, observed);
    }

    @Transactional(propagation = Propagation.NEVER)
    public MembershipRefundExecutionResult reconcileObserved(Long memberId, Long attemptId, PortOnePayment observed) {
        return outcomeWithoutToken(resultProcessor.apply(memberId, attemptId, null, observed));
    }

    // ---------- POST ----------

    private MembershipRefundExecutionResult dispatch(MembershipRefundTicket ticket) {
        LocalDateTime now = LocalDateTime.now(clock);
        if (ticket.firstDispatchedAt() != null && !now.isBefore(ticket.firstDispatchedAt().plus(REPOST_WINDOW))) {
            // 같은 키 재POST 창이 끝남. GET 정합화만 계속한다(24시간 뒤 운영 확인)
            return nextCheckOrExpire(ticket, "REPOST_WINDOW_CLOSED");
        }
        Optional<MembershipRefundTicket> prepared = executionProcessor.prepareFirstDispatch(ticket);
        if (prepared.isEmpty()) {
            // 토큰·상태 변경, 회차 강한 차단, 재POST 창 종료 중 하나. 보내지 않고 임대만 돌려준다(토큰이 우리 것일 때만) —
            // 복구 스캔이 다음 확인을 이어 간다(차단된 회차는 실행권을 잡지 않으므로 운영 확인에 남는다)
            log.info("POST 준비 재검증 실패 — 보내지 않음: refundId={}", ticket.refundId());
            executionProcessor.releaseLease(ticket);
            return MembershipRefundExecutionResult.PENDING;
        }
        MembershipRefundTicket sent = prepared.get();
        PortOneMembershipCancelResult receipt = refundClient.cancel(sent.portonePaymentId(), sent.pgIdempotencyKey(),
            new PortOneMembershipCancelRequest(sent.pgStoreId(), sent.amount(), sent.paidAmountSnapshot(),
                sent.requester() == RefundRequester.MEMBER ? PortOneMembershipCancelRequest.CUSTOMER
                    : PortOneMembershipCancelRequest.ADMIN, sent.pgRequestReason()));
        log.info("멤버십 취소 호출 완료(재조회로 확정): refundId={}, outcome={}", sent.refundId(), receipt.outcome());
        switch (receipt.outcome()) {
            case SUCCEEDED, REQUESTED, FAILED -> {
                boolean recorded = executionProcessor.recordCancellationReceipt(sent, receipt);
                if (!recorded) {
                    log.warn("취소 응답 ID를 기록하지 않음 — GET 정합화로 확인: refundId={}", sent.refundId());
                }
                return verifyAfterPost(sent);
            }
            case MISMATCH -> {
                // 취소 가능 잔액·상태 불일치. 새 키를 만들지 않고 기존 취소를 GET으로 확인한다
                return verifyAfterPost(sent);
            }
            case REJECTED -> {
                MembershipRefundOpsReason reason = receipt.isAuthError() ? MembershipRefundOpsReason.PG_AUTH_ERROR
                    : MembershipRefundOpsReason.PG_REQUEST_REJECTED;
                executionProcessor.haltAuto(sent, reason);
                return MembershipRefundExecutionResult.REVIEW_REQUIRED;
            }
            case UNCONFIRMED -> {
                // timeout·5xx·처리 중 409·해석 불가. REQUESTED 유지, 같은 키로 나중에 재확인
                return nextCheckOrExpire(sent, "POST_UNCONFIRMED");
            }
        }
        throw new IllegalStateException("취소 응답 분류를 처리하지 못함: " + receipt.outcome());
    }

    // POST 뒤 같은 결제 ID GET → TX2. POST는 다시 보내지 않는다
    private MembershipRefundExecutionResult verifyAfterPost(MembershipRefundTicket ticket) {
        Lookup lookup = lookup(ticket.portonePaymentId());
        if (lookup.payment() == null) {
            return nextCheckOrExpire(ticket, "GET_" + lookup.code());
        }
        MembershipRefundApplyResult applied = resultProcessor.apply(ticket.memberId(), ticket.attemptId(),
            ticket.refundId(), lookup.payment());
        return settle(ticket, applied);
    }

    // ---------- 예약·종료 ----------

    // TX2 결과를 실행권 보유자 입장에서 마무리한다. 확정·차단이면 임대 해제, 불신뢰·발송 불명 신호면 자동 중단 표시, 미확정이면 다음 확인 예약
    private MembershipRefundExecutionResult settle(MembershipRefundTicket ticket, MembershipRefundApplyResult applied) {
        if (applied.signal() == Signal.UNTRUSTED_OBSERVATION) {
            executionProcessor.haltAuto(ticket, MembershipRefundOpsReason.CANCELLATION_STATUS_UNKNOWN);
            return MembershipRefundExecutionResult.REVIEW_REQUIRED;
        }
        if (applied.signal() == Signal.LEGACY_UNPROVEN) {
            executionProcessor.haltAuto(ticket, MembershipRefundOpsReason.LEGACY_DISPATCH_UNPROVEN);
            return MembershipRefundExecutionResult.REVIEW_REQUIRED;
        }
        return switch (applied.outcome()) {
            case COMPLETED, FAILED, REVIEW_REQUIRED, SKIPPED -> {
                executionProcessor.releaseLease(ticket);
                yield applied.outcome();
            }
            case PENDING -> nextCheckOrExpire(ticket, "UNRESOLVED");
        };
    }

    // 이번 관측으로 확정하지 못함. 기준 + 24시간 이상이면 운영 확인(UNKNOWN_WINDOW_EXPIRED), 아니면 경과시간 구간으로 다음 확인 예약
    private MembershipRefundExecutionResult nextCheckOrExpire(MembershipRefundTicket ticket, String cause) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime base = ticket.checkBaseAt();
        if (!now.isBefore(base.plus(UNKNOWN_WINDOW))) {
            log.warn("환불 결과 확인 창이 끝나 자동 조회를 멈춤(운영 확인): refundId={}, base={}, cause={}", ticket.refundId(), base,
                cause);
            executionProcessor.haltAuto(ticket, MembershipRefundOpsReason.UNKNOWN_WINDOW_EXPIRED);
            return MembershipRefundExecutionResult.REVIEW_REQUIRED;
        }
        LocalDateTime next = now.plus(nextCheckDelay(Duration.between(base, now)));
        if (!executionProcessor.scheduleNextCheck(ticket, next)) {
            log.info("다음 확인 예약을 덮어쓰지 않음(실행권이 바뀜): refundId={}", ticket.refundId());
        }
        return MembershipRefundExecutionResult.PENDING;
    }

    // 확인 기준부터의 경과시간 구간 → 다음 지연. 1분 미만 1분, 3분 미만 2분, 7분 미만 4분, 15분 미만 8분, 그 이상 10분
    static Duration nextCheckDelay(Duration elapsed) {
        if (elapsed.isNegative() || elapsed.compareTo(Duration.ofMinutes(1)) < 0) {
            return DELAY_MIN;
        }
        if (elapsed.compareTo(Duration.ofMinutes(3)) < 0) {
            return Duration.ofMinutes(2);
        }
        if (elapsed.compareTo(Duration.ofMinutes(7)) < 0) {
            return Duration.ofMinutes(4);
        }
        if (elapsed.compareTo(Duration.ofMinutes(15)) < 0) {
            return Duration.ofMinutes(8);
        }
        return DELAY_MAX;
    }

    // 토큰 없는 경로(관리자·웹훅)의 결과. 신호(불신뢰·발송 불명)는 운영 표시로 바꾸지 않고 미확정으로 돌려준다
    private static MembershipRefundExecutionResult outcomeWithoutToken(MembershipRefundApplyResult applied) {
        return applied.outcome();
    }

    // ---------- GET ----------

    private record Lookup(PortOnePayment payment, String code) {
    }

    // 같은 결제 ID 조회. 실패는 코드만 남긴다 — 예외 객체(응답 원문)는 넘기지 않는다
    private Lookup lookup(String portonePaymentId) {
        try {
            return new Lookup(portOnePaymentClient.getPayment(portonePaymentId), null);
        } catch (BusinessException e) {
            return new Lookup(null, e.getErrorCode().getCode());
        }
    }

    // 관리자 응답용 관측값: 이 환불에 매칭되는 취소 내역(저장 ID 또는 서버 생성 reason)이 있으면 함께 담는다
    private RefundPgObservation observe(MembershipRefundRef ref, PortOnePayment payment) {
        List<RefundCancellationBinding> bindings = List.of(new RefundCancellationBinding(ref.refundId(), ref.status(),
            ref.portoneCancellationId(), ref.pgRequestReason()));
        RefundMatchSnapshot snapshot = new RefundMatchSnapshot(ref.refundId(), ref.portonePaymentId(),
            portOneProperties.storeId(), null, ref.amount() == null ? 0L : ref.amount(), ref.pgRequestReason(),
            ref.portoneCancellationId(), ref.status());
        Match match = MembershipRefundCancellationMatcher.match(snapshot, payment, bindings);
        return RefundPgObservation.of(payment, match.cancellation());
    }
}
