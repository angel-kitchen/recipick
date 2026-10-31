package com.recipick.domain.payment.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.recipick.domain.order.entity.enums.MartStatus;
import com.recipick.domain.order.entity.enums.OrderStatus;
import com.recipick.domain.payment.entity.MemberPoint;
import com.recipick.domain.payment.entity.PointEarning;
import com.recipick.domain.payment.entity.PointUsage;
import com.recipick.domain.payment.entity.enums.PaymentStatus;
import com.recipick.domain.payment.entity.enums.PointEarningStatus;
import com.recipick.domain.payment.entity.enums.PointHistoryType;
import com.recipick.domain.payment.entity.enums.PointUsageStatus;
import com.recipick.domain.payment.facade.ExternalCancellationProcessor;
import com.recipick.domain.payment.repository.MemberPointRepository;
import com.recipick.domain.payment.repository.PointEarningRepository;
import com.recipick.domain.payment.repository.PointUsageRepository;
import com.recipick.domain.payment.service.ExternalCancellationOutcome;
import com.recipick.domain.payment.service.ExternalCancellationResult;
import com.recipick.domain.payment.service.PointCommandService;
import com.recipick.domain.payment.service.PointRestoreOutcome;
import com.recipick.infra.portone.dto.PortOneCancellation;
import com.recipick.infra.portone.dto.PortOneCancellationStatus;
import com.recipick.infra.portone.dto.PortOneCancellationTrigger;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

// 탈퇴로 소멸(FORFEITED)된 적립 건의 주문에 뒤늦게 외부 취소가 들어와도 결제·주문 정합화는 끝나고 포인트는 되돌리지 않는지 본다.
// 확정(COMPLETED) 주문의 외부 취소가 포인트를 회수하지 않는 기존 규칙(PaymentPointIntegrationTest)과 같은 결과여야 한다 —
// FORFEITED도 "거래가 끝난 적립"이기 때문이다. 포인트 픽스처는 PaymentPointIntegrationTest와 같은 숫자를 쓴다
class PointExternalCancelAfterForfeitIntegrationTest extends PaymentFlowIntegrationSupport {

    private static final long HELD_POINT = 1_000L;
    private static final long USED_POINT = 300L;
    private static final long PRODUCT_AMOUNT = 20_800L;
    private static final BigDecimal RATE = new BigDecimal("0.0050");
    // (20,800 - 300) × 0.5% = 102.5 → 원 미만 버림
    private static final long EARNED_POINT = 102L;
    // 적립 건의 주문 ID는 실제 주문과 겹치지 않게 크게 띄운다(point_earning.order_id UNIQUE)
    private static final long LOT_ORDER_OFFSET = 9_400_000_000L;

    @Autowired
    PointCommandService pointCommandService;

    @Autowired
    MemberPointRepository memberPointRepository;

    @Autowired
    PointEarningRepository pointEarningRepository;

    @Autowired
    PointUsageRepository pointUsageRepository;

    @Autowired
    ExternalCancellationProcessor externalCancellationProcessor;

    private Long pointMemberId;

    // 포인트 행을 부모의 주문·결제 정리보다 먼저, 외래키 역순(이력 → 사용 → 적립 → 회원 포인트)으로 지운다
    @AfterEach
    void cleanUpPoints() {
        if (pointMemberId == null) {
            return;
        }
        jdbcTemplate.update("DELETE FROM point_history WHERE member_id = ?", pointMemberId);
        jdbcTemplate.update("DELETE pu FROM point_usage pu JOIN point_earning pe ON pu.point_earning_id = pe.id "
            + "WHERE pe.member_id = ?", pointMemberId);
        jdbcTemplate.update("DELETE FROM point_earning WHERE member_id = ?", pointMemberId);
        jdbcTemplate.update("DELETE FROM member_point WHERE member_id = ?", pointMemberId);
        pointMemberId = null;
    }

    @Test
    @DisplayName("탈퇴로 소멸된 적립 건의 주문을 콘솔에서 전액 취소하면 결제·주문은 정합화되고 포인트는 재지급되지 않는다")
    void externalCancelAfterForfeitKeepsForfeitedEarning() {
        // given — 배송완료로 적립이 확정됐고(702 + 102 = 802P), 회원이 802를 확인하고 탈퇴 정산으로 전부 소멸시켰다
        Flow flow = paidPaymentWithStock(MartStatus.DELIVERED);
        attachPoints(flow);
        transactionTemplate.executeWithoutResult(status -> pointCommandService.confirmEarning(flow.orderId()));
        long balanceBeforeWithdrawal = HELD_POINT - USED_POINT + EARNED_POINT;
        assertThat(balanceOf(flow)).isEqualTo(balanceBeforeWithdrawal);
        transactionTemplate.executeWithoutResult(
            status -> pointCommandService.forfeitForWithdrawal(flow.memberId(), balanceBeforeWithdrawal));
        assertThat(earningStatus(flow)).isEqualTo(PointEarningStatus.FORFEITED);
        assertThat(balanceOf(flow)).isZero();
        LocalDateTime now = LocalDateTime.now(clock).withNano(0);
        PortOneCancellation cancellation = new PortOneCancellation("cancel_console_" + flow.orderId(),
            PortOneCancellationStatus.SUCCEEDED, AMOUNT, now.minusMinutes(1), now, PortOneCancellationTrigger.CONSOLE);

        // when
        ExternalCancellationResult result = externalCancellationProcessor.apply(flow.paymentId(), cancellation);

        // then — PG 사실대로 결제·주문은 닫히고, 포인트는 소멸된 그대로다(재지급·복원·취소 이력 없음)
        assertThat(result.outcome()).isEqualTo(ExternalCancellationOutcome.RECONCILED);
        assertThat(paymentOf(flow).getStatus()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(orderOf(flow).getStatus()).isEqualTo(OrderStatus.REFUNDED);
        assertThat(earningStatus(flow)).isEqualTo(PointEarningStatus.FORFEITED);
        assertThat(pointEarningRepository.findByOrderId(flow.orderId()).orElseThrow().getRemainingAmount()).isZero();
        assertThat(usageStatus(flow)).isEqualTo(PointUsageStatus.CONFIRMED);
        assertThat(balanceOf(flow)).isZero();
        assertThat(historyCount(flow, PointHistoryType.RESTORED)).isZero();
        assertThat(historyCount(flow, PointHistoryType.EARN_CANCELED)).isZero();
        // 소멸 이력은 적립 건 두 개(보유 건 700P + 이 주문의 102P) 그대로다
        assertThat(historyCount(flow, PointHistoryType.WITHDRAWAL_FORFEITED)).isEqualTo(2);
        assertThat(historyAmount(flow, PointHistoryType.WITHDRAWAL_FORFEITED)).isEqualTo(balanceBeforeWithdrawal);
    }

    @Test
    @DisplayName("소멸된 적립 건의 외부 취소 복원은 FINALIZED_EARNING_KEPT로 답하고 아무것도 바꾸지 않는다")
    void restoreForExternalCancelKeepsForfeitedEarning() {
        // given
        Flow flow = paidPaymentWithStock(MartStatus.DELIVERED);
        attachPoints(flow);
        transactionTemplate.executeWithoutResult(status -> pointCommandService.confirmEarning(flow.orderId()));
        long balanceBeforeWithdrawal = HELD_POINT - USED_POINT + EARNED_POINT;
        transactionTemplate.executeWithoutResult(
            status -> pointCommandService.forfeitForWithdrawal(flow.memberId(), balanceBeforeWithdrawal));

        // when
        PointRestoreOutcome outcome = transactionTemplate.execute(
            status -> pointCommandService.restoreForExternalCancel(flow.orderId()));

        // then
        assertThat(outcome).isEqualTo(PointRestoreOutcome.FINALIZED_EARNING_KEPT);
        assertThat(earningStatus(flow)).isEqualTo(PointEarningStatus.FORFEITED);
        assertThat(usageStatus(flow)).isEqualTo(PointUsageStatus.CONFIRMED);
        assertThat(balanceOf(flow)).isZero();
        assertThat(historyCount(flow, PointHistoryType.RESTORED)).isZero();
    }

    // 주문 생성·결제 성공이 할 일을 흉내 낸다: 1,000P(1년 뒤 만료)를 가진 회원이 이 주문에서 300P를 쓰고 적립 예정을 만든 뒤 결제가 끝났다
    private void attachPoints(Flow flow) {
        pointMemberId = flow.memberId();
        transactionTemplate.executeWithoutResult(status -> {
            PointEarning lot = PointEarning.pending(flow.memberId(), LOT_ORDER_OFFSET + flow.orderId(), HELD_POINT);
            lot.complete(LocalDateTime.now(clock).minusDays(1));
            pointEarningRepository.save(lot);
            memberPointRepository.save(MemberPoint.builder().memberId(flow.memberId()).balance(HELD_POINT).build());
            pointCommandService.reserveUsage(flow.memberId(), flow.orderId(), USED_POINT);
            pointCommandService.createPendingEarning(
                flow.memberId(), flow.orderId(), PRODUCT_AMOUNT, USED_POINT, RATE);
            pointCommandService.confirmUsage(flow.orderId());
        });
    }

    private PointUsageStatus usageStatus(Flow flow) {
        List<PointUsage> usages = pointUsageRepository.findAllByOrderIdOrderByIdAsc(flow.orderId());
        assertThat(usages).hasSize(1);
        return usages.get(0).getStatus();
    }

    private PointEarningStatus earningStatus(Flow flow) {
        return pointEarningRepository.findByOrderId(flow.orderId()).orElseThrow().getStatus();
    }

    // 잠금 조회는 트랜잭션이 필요해 값만 직접 읽는다
    private long balanceOf(Flow flow) {
        return jdbcTemplate.queryForObject("SELECT balance FROM member_point WHERE member_id = ?",
            Long.class, flow.memberId());
    }

    private int historyCount(Flow flow, PointHistoryType type) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM point_history WHERE member_id = ? AND type = ?",
            Integer.class, flow.memberId(), type.name());
    }

    private long historyAmount(Flow flow, PointHistoryType type) {
        return jdbcTemplate.queryForObject(
            "SELECT COALESCE(SUM(amount), 0) FROM point_history WHERE member_id = ? AND type = ?",
            Long.class, flow.memberId(), type.name());
    }
}
