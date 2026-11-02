package com.recipick.domain.payment.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.recipick.domain.member.service.MembershipBillingService;
import com.recipick.domain.payment.entity.BillingKey;
import com.recipick.domain.payment.entity.MembershipPayment;
import com.recipick.domain.payment.entity.MembershipPaymentAttempt;
import com.recipick.domain.payment.entity.MembershipRefund;
import com.recipick.domain.payment.entity.enums.BillingKeyStatus;
import com.recipick.domain.payment.entity.enums.MembershipRefundOpsReason;
import com.recipick.domain.payment.entity.enums.MembershipRefundStatus;
import com.recipick.domain.payment.facade.MembershipRefundExecutionProcessor.Claim;
import com.recipick.domain.payment.repository.MembershipPaymentAttemptRepository;
import com.recipick.domain.payment.repository.MembershipRefundRepository;
import com.recipick.domain.payment.service.MembershipRefundCalculator.Calculation;
import com.recipick.domain.payment.service.MembershipRefundTicket;
import com.recipick.infra.portone.PortOneProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

// 실행권 TX의 고정 요청값 처리 — 첫 실행에서 상점 ID가 비어 있을 때 NULL 비교 오류 없이 서버 설정으로 채우거나, 설정이 비어 있으면
// PG 호출 없이 PG_CONFIG_ERROR 운영 확인으로 남긴다. 예약된 다음 확인 시각 전의 실행권 거절, POST 직전의 회차 차단·재POST 창 재확인,
// 임대가 끝난 실행자의 예약·중단 거절도 본다. DB 없이 Repository를 Mock으로 둔다
class MembershipRefundExecutionProcessorTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2027, 3, 10, 15, 0);
    private static final Clock CLOCK = Clock.fixed(NOW.atZone(ZoneId.of("Asia/Seoul")).toInstant(),
        ZoneId.of("Asia/Seoul"));

    private final MembershipRefundRepository refunds = mock(MembershipRefundRepository.class);
    private final MembershipPaymentAttemptRepository attempts = mock(MembershipPaymentAttemptRepository.class);
    private final MembershipBillingService membershipBillingService = mock(MembershipBillingService.class);

    @Test
    @DisplayName("상점 ID가 비어 있는 첫 실행은 서버 설정의 상점 ID와 고정 요청 사유를 채우고 티켓을 돌려준다")
    void firstClaimFixesStoreIdFromConfiguration() {
        // given
        MembershipRefund refund = requestedMemberRefund(10L);
        given(refunds.findByIdForUpdate(10L)).willReturn(Optional.of(refund));
        given(attempts.existsByMembershipPaymentIdAndOpsReviewReasonIn(anyLong(), any())).willReturn(false);
        given(membershipBillingService.findMemberIdOfMembership(anyLong())).willReturn(7L);
        MembershipRefundExecutionProcessor processor = new MembershipRefundExecutionProcessor(refunds, attempts,
            membershipBillingService, properties("store-configured"), CLOCK);

        // when
        Claim claim = processor.claim(10L);

        // then
        assertThat(claim.ticket()).isPresent();
        assertThat(claim.halted()).isFalse();
        assertThat(claim.ticket().get().pgStoreId()).isEqualTo("store-configured");
        assertThat(claim.ticket().get().pgRequestReason())
            .isEqualTo(MembershipRefund.PG_REASON_PREFIX + "10_" + refund.getPgIdempotencyKey());
        assertThat(claim.ticket().get().claimToken()).isEqualTo(1L);
        assertThat(claim.ticket().get().memberId()).isEqualTo(7L);
        assertThat(refund.getPgStoreId()).isEqualTo("store-configured");
        assertThat(refund.getFirstDispatchedAt()).isNull();
    }

    @Test
    @DisplayName("서버 상점 설정이 비어 있으면 PG 호출 없이 PG_CONFIG_ERROR 운영 확인으로 남기고 티켓을 주지 않는다")
    void blankStoreConfigurationHaltsWithConfigError() {
        // given
        MembershipRefund refund = requestedMemberRefund(11L);
        given(refunds.findByIdForUpdate(11L)).willReturn(Optional.of(refund));
        given(attempts.existsByMembershipPaymentIdAndOpsReviewReasonIn(anyLong(), any())).willReturn(false);
        MembershipRefundExecutionProcessor processor = new MembershipRefundExecutionProcessor(refunds, attempts,
            membershipBillingService, properties(" "), CLOCK);

        // when
        Claim claim = processor.claim(11L);

        // then
        assertThat(claim.ticket()).isEmpty();
        assertThat(claim.halted()).isTrue();
        assertThat(refund.getStatus()).isEqualTo(MembershipRefundStatus.REQUESTED);
        assertThat(refund.getOpsReviewReason()).isEqualTo(MembershipRefundOpsReason.PG_CONFIG_ERROR);
        assertThat(refund.getPgStoreId()).isNull();
        assertThat(refund.getLeaseExpiresAt()).isNull();
        verify(membershipBillingService, never()).findMemberIdOfMembership(anyLong());
    }

    @Test
    @DisplayName("이미 고정된 상점 ID는 설정이 바뀌어도 덮어쓰지 않는다")
    void fixedStoreIdIsNotOverwritten() {
        // given
        MembershipRefund refund = requestedMemberRefund(12L);
        refund.fixDispatchRequest("store-old");
        given(refunds.findByIdForUpdate(12L)).willReturn(Optional.of(refund));
        given(attempts.existsByMembershipPaymentIdAndOpsReviewReasonIn(anyLong(), any())).willReturn(false);
        given(membershipBillingService.findMemberIdOfMembership(anyLong())).willReturn(7L);
        MembershipRefundExecutionProcessor processor = new MembershipRefundExecutionProcessor(refunds, attempts,
            membershipBillingService, properties("store-new"), CLOCK);

        // when
        Claim claim = processor.claim(12L);

        // then
        assertThat(claim.ticket().orElseThrow().pgStoreId()).isEqualTo("store-old");
    }

    @Test
    @DisplayName("예약된 다음 확인 시각이 아직 오지 않은 환불은 실행권을 잡지 않는다(토큰·임대 변화 없음). 예약이 없으면 바로 잡는다")
    void claimRefusedBeforeScheduledNextCheck() {
        // given
        MembershipRefund scheduled = requestedMemberRefund(13L);
        scheduled.scheduleNextCheck(NOW.plusSeconds(1));
        MembershipRefund unscheduled = requestedMemberRefund(14L);
        given(refunds.findByIdForUpdate(13L)).willReturn(Optional.of(scheduled));
        given(refunds.findByIdForUpdate(14L)).willReturn(Optional.of(unscheduled));
        given(attempts.existsByMembershipPaymentIdAndOpsReviewReasonIn(anyLong(), any())).willReturn(false);
        given(membershipBillingService.findMemberIdOfMembership(anyLong())).willReturn(7L);
        MembershipRefundExecutionProcessor processor = new MembershipRefundExecutionProcessor(refunds, attempts,
            membershipBillingService, properties("store-configured"), CLOCK);

        // when
        Claim early = processor.claim(13L);
        Claim due = processor.claim(14L);

        // then
        assertThat(early.ticket()).isEmpty();
        assertThat(early.halted()).isFalse();
        assertThat(scheduled.getClaimToken()).isZero();
        assertThat(scheduled.getLeaseExpiresAt()).isNull();
        assertThat(due.ticket()).isPresent();
    }

    @Test
    @DisplayName("POST 준비는 실행권 뒤 같은 회차에 강한 차단이 생겼거나 재POST 창(최초 발송 + 2시간 55분)이 끝났으면 거절하고 발송 시각을 남기지 않는다")
    void prepareRefusedWhenRoundBlockedOrRepostWindowClosed() {
        // given
        MembershipRefund blockedRound = requestedMemberRefund(15L);
        MembershipRefund windowClosed = requestedMemberRefund(16L);
        windowClosed.markFirstDispatched(NOW.minusHours(2).minusMinutes(55));
        MembershipRefund insideWindow = requestedMemberRefund(17L);
        insideWindow.markFirstDispatched(NOW.minusHours(2).minusMinutes(54));
        given(refunds.findByIdForUpdate(15L)).willReturn(Optional.of(blockedRound));
        given(refunds.findByIdForUpdate(16L)).willReturn(Optional.of(windowClosed));
        given(refunds.findByIdForUpdate(17L)).willReturn(Optional.of(insideWindow));
        given(attempts.existsByMembershipPaymentIdAndOpsReviewReasonIn(anyLong(), any())).willReturn(false);
        given(membershipBillingService.findMemberIdOfMembership(anyLong())).willReturn(7L);
        MembershipRefundExecutionProcessor processor = new MembershipRefundExecutionProcessor(refunds, attempts,
            membershipBillingService, properties("store-configured"), CLOCK);
        MembershipRefundTicket blockedTicket = processor.claim(15L).ticket().orElseThrow();
        MembershipRefundTicket closedTicket = processor.claim(16L).ticket().orElseThrow();
        MembershipRefundTicket insideTicket = processor.claim(17L).ticket().orElseThrow();
        given(attempts.existsByMembershipPaymentIdAndOpsReviewReasonIn(anyLong(), any())).willReturn(true, false, false);

        // when
        Optional<MembershipRefundTicket> blocked = processor.prepareFirstDispatch(blockedTicket);
        Optional<MembershipRefundTicket> closed = processor.prepareFirstDispatch(closedTicket);
        Optional<MembershipRefundTicket> inside = processor.prepareFirstDispatch(insideTicket);

        // then
        assertThat(blocked).isEmpty();
        assertThat(blockedRound.getFirstDispatchedAt()).isNull();
        assertThat(closed).isEmpty();
        assertThat(inside).isPresent();
        assertThat(inside.get().firstDispatchedAt()).isEqualTo(NOW.minusHours(2).minusMinutes(54));
    }

    @Test
    @DisplayName("임대가 끝난 실행자는 토큰이 그대로여도 다음 확인 예약·자동 중단·임대 해제를 할 수 없다")
    void expiredLeaseCannotScheduleHaltOrRelease() {
        // given
        MembershipRefund refund = requestedMemberRefund(18L);
        given(refunds.findByIdForUpdate(18L)).willReturn(Optional.of(refund));
        given(attempts.existsByMembershipPaymentIdAndOpsReviewReasonIn(anyLong(), any())).willReturn(false);
        given(membershipBillingService.findMemberIdOfMembership(anyLong())).willReturn(7L);
        Clock earlier = Clock.fixed(NOW.minusMinutes(5).atZone(ZoneId.of("Asia/Seoul")).toInstant(),
            ZoneId.of("Asia/Seoul"));
        MembershipRefundTicket expired = new MembershipRefundExecutionProcessor(refunds, attempts,
            membershipBillingService, properties("store-configured"), earlier).claim(18L).ticket().orElseThrow();
        MembershipRefundExecutionProcessor now = new MembershipRefundExecutionProcessor(refunds, attempts,
            membershipBillingService, properties("store-configured"), CLOCK);

        // when
        boolean scheduled = now.scheduleNextCheck(expired, NOW.plusMinutes(1));
        boolean halted = now.haltAuto(expired, MembershipRefundOpsReason.UNKNOWN_WINDOW_EXPIRED);
        now.releaseLease(expired);

        // then
        assertThat(expired.leaseExpiresAt()).isEqualTo(NOW);
        assertThat(scheduled).isFalse();
        assertThat(halted).isFalse();
        assertThat(refund.getNextCheckAt()).isNull();
        assertThat(refund.getOpsReviewReason()).isNull();
        assertThat(refund.getLeaseExpiresAt()).isEqualTo(NOW);
        assertThat(refund.getClaimToken()).isEqualTo(expired.claimToken());
    }

    private static PortOneProperties properties(String storeId) {
        return new PortOneProperties("secret", "https://api.portone.io", Duration.ofSeconds(1), Duration.ofSeconds(1),
            storeId, "channel", Duration.ofSeconds(60));
    }

    private static MembershipRefund requestedMemberRefund(Long refundId) {
        MembershipPayment payment = MembershipPayment.pendingFirst(10L, null, null, 3_900L,
            LocalDate.of(2027, 2, 28), LocalDate.of(2027, 3, 30));
        ReflectionTestUtils.setField(payment, "id", 500L);
        BillingKey key = BillingKey.builder().memberId(7L).portoneBillingKey("billing-key-test-0001")
            .status(BillingKeyStatus.ACTIVE).issuedAt(NOW).build();
        LocalDateTime paidAt = LocalDateTime.of(2027, 2, 28, 10, 3);
        MembershipPaymentAttempt attempt = MembershipPaymentAttempt.ready(payment, 1, key, paidAt.minusMinutes(5),
            Duration.ofMinutes(10));
        ReflectionTestUtils.setField(attempt, "id", 42L);
        attempt.claim(paidAt.minusMinutes(1));
        attempt.markPaid(paidAt, paidAt.plusMinutes(1));
        payment.markPaid(paidAt, paidAt.plusMinutes(1));
        MembershipRefund refund = MembershipRefund.requestedByMember(payment, attempt,
            new Calculation(31, 10, 1_258L, 780L, 1_862L), "mrf_member:7:k" + refundId, "hash", paidAt,
            LocalDateTime.of(2027, 3, 31, 10, 3), NOW.minusMinutes(1));
        ReflectionTestUtils.setField(refund, "id", refundId);
        return refund;
    }
}
