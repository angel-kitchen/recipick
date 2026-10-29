package com.recipick.domain.payment.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.recipick.domain.member.entity.enums.AdminRole;
import com.recipick.domain.order.entity.enums.MartStatus;
import com.recipick.domain.order.entity.enums.OrderStatus;
import com.recipick.domain.payment.dto.MartRejectionPreparation;
import com.recipick.domain.payment.dto.PaymentCancelPreparation;
import com.recipick.domain.payment.entity.MemberPoint;
import com.recipick.domain.payment.entity.PointEarning;
import com.recipick.domain.payment.entity.PointUsage;
import com.recipick.domain.payment.entity.enums.PaymentStatus;
import com.recipick.domain.payment.entity.enums.PointEarningStatus;
import com.recipick.domain.payment.entity.enums.PointHistoryType;
import com.recipick.domain.payment.entity.enums.PointUsageStatus;
import com.recipick.domain.payment.facade.ExternalCancellationProcessor;
import com.recipick.domain.payment.facade.MartRejectionProcessor;
import com.recipick.domain.payment.facade.PaymentCancelProcessor;
import com.recipick.domain.payment.facade.PaymentConfirmProcessor;
import com.recipick.domain.payment.facade.PaymentExpirationProcessor;
import com.recipick.domain.payment.repository.MemberPointRepository;
import com.recipick.domain.payment.repository.PointEarningRepository;
import com.recipick.domain.payment.repository.PointUsageRepository;
import com.recipick.domain.payment.service.ExternalCancellationOutcome;
import com.recipick.domain.payment.service.ExternalCancellationResult;
import com.recipick.domain.payment.service.PaymentApprovalOutcome;
import com.recipick.domain.payment.service.PaymentExpirationOutcome;
import com.recipick.domain.payment.service.PointCommandService;
import com.recipick.global.audit.AdminAuditRecorder;
import com.recipick.infra.portone.dto.PortOneCancelOutcome;
import com.recipick.infra.portone.dto.PortOneCancelResult;
import com.recipick.infra.portone.dto.PortOneCancellation;
import com.recipick.infra.portone.dto.PortOneCancellationStatus;
import com.recipick.infra.portone.dto.PortOneCancellationTrigger;
import com.recipick.infra.portone.dto.PortOnePayment;
import com.recipick.infra.portone.dto.PortOnePaymentStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

// 기존 CARD 결제 흐름의 각 종료 지점이 포인트를 실제 MySQL 위에서 정확히 한 번 확정·복원하는지 본다.
// 주문 생성 연동(4단계) 전이라 주문 생성이 할 일 — 1,000P 보유 회원이 300P를 예약하고 적립 예정을 만드는 것 — 을 픽스처가 대신한다.
// FEFO·만료 복원 같은 포인트 내부 규칙은 PointCommandServiceTest가 맡고, 여기서는 결제 흐름과의 연결만 본다
class PaymentPointIntegrationTest extends PaymentFlowIntegrationSupport {

    private static final long HELD_POINT = 1_000L;
    private static final long USED_POINT = 300L;
    // 적립 기준인 배송비 제외 상품금액. 기존 PaymentFlow의 AMOUNT는 1차 결제 픽스처의 외부 결제금액이라 쓰지 않는다 —
    // 상품금액·사용 포인트를 실제 주문·결제 금액에 반영하는 것은 4단계 주문 생성 연동에서 한다
    private static final long PRODUCT_AMOUNT = 20_800L;
    private static final BigDecimal RATE = new BigDecimal("0.0050");
    // 적립 건의 주문 ID는 실제 주문과 겹치지 않게 크게 띄운다(point_earning.order_id UNIQUE)
    private static final long LOT_ORDER_OFFSET = 9_300_000_000L;
    private static final Long ADMIN_ID = 17L;
    private static final String REJECT_REASON = "상품 준비가 어렵습니다.";

    // 마트 거절 감사 기록은 실제 관리자 행이 필요해 이 테스트에서는 대신한다(MartRejectionConcurrencyTest와 같은 구성)
    @MockitoBean
    AdminAuditRecorder adminAuditRecorder;

    @Autowired
    PointCommandService pointCommandService;

    @Autowired
    MemberPointRepository memberPointRepository;

    @Autowired
    PointEarningRepository pointEarningRepository;

    @Autowired
    PointUsageRepository pointUsageRepository;

    @Autowired
    PaymentConfirmProcessor paymentConfirmProcessor;

    @Autowired
    PaymentExpirationProcessor paymentExpirationProcessor;

    @Autowired
    PaymentCancelProcessor paymentCancelProcessor;

    @Autowired
    MartRejectionProcessor martRejectionProcessor;

    @Autowired
    ExternalCancellationProcessor externalCancellationProcessor;

    private final List<Long> pointMemberIds = new ArrayList<>();

    // 포인트 행을 부모의 주문·결제 정리보다 먼저, 외래키 역순(이력 → 사용 → 적립 → 회원 포인트)으로 지운다
    @AfterEach
    void cleanUpPoints() {
        for (Long memberId : pointMemberIds) {
            jdbcTemplate.update("DELETE FROM point_history WHERE member_id = ?", memberId);
            jdbcTemplate.update("DELETE pu FROM point_usage pu JOIN point_earning pe ON pu.point_earning_id = pe.id "
                + "WHERE pe.member_id = ?", memberId);
            jdbcTemplate.update("DELETE FROM point_earning WHERE member_id = ?", memberId);
            jdbcTemplate.update("DELETE FROM member_point WHERE member_id = ?", memberId);
        }
        pointMemberIds.clear();
    }

    @Test
    @DisplayName("결제 승인이면 예약한 포인트가 CONFIRMED가 되고 적립 예정은 PENDING으로 남으며 잔액은 다시 줄지 않는다")
    void approvalConfirmsUsage() {
        // given
        Flow flow = pendingPayment();
        attachPoints(flow, false);

        // when
        PaymentApprovalOutcome outcome = paymentConfirmProcessor.process(flow.orderId(), portOnePaid(flow));

        // then
        assertThat(outcome).isEqualTo(PaymentApprovalOutcome.APPROVED);
        assertThat(orderOf(flow).getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(usageStatus(flow)).isEqualTo(PointUsageStatus.CONFIRMED);
        assertThat(earningStatus(flow)).isEqualTo(PointEarningStatus.PENDING);
        assertThat(balanceOf(flow)).isEqualTo(HELD_POINT - USED_POINT);
        assertThat(historyCount(flow, PointHistoryType.USE_CONFIRMED)).isEqualTo(1);
    }

    @Test
    @DisplayName("PG가 결제 실패를 확인하면 주문을 닫고 예약 포인트를 되돌리며 적립 예정을 취소한다")
    void pgFailureRestoresPoints() {
        // given
        Flow flow = pendingPayment();
        attachPoints(flow, false);
        PortOnePayment failed = new PortOnePayment(flow.portonePaymentId(), "tx_flow_" + flow.orderId(),
            PortOnePaymentStatus.FAILED, null, "KRW", null, flow.orderId(), "card declined");

        // when
        PaymentApprovalOutcome outcome = paymentConfirmProcessor.process(flow.orderId(), failed);

        // then
        assertThat(outcome).isEqualTo(PaymentApprovalOutcome.PG_FAILED);
        assertThat(orderOf(flow).getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertRestored(flow);
    }

    @Test
    @DisplayName("결제 대기 5분이 지나면 주문을 닫고 예약 포인트를 되돌린 뒤 재고를 복구한다")
    void expirationRestoresPoints() {
        // given
        Flow flow = pendingPaymentWithStock(LocalDateTime.now(clock).withNano(0).minusMinutes(1));
        attachPoints(flow, false);

        // when
        PaymentExpirationOutcome outcome = paymentExpirationProcessor.expire(flow.paymentId());

        // then
        assertThat(outcome).isEqualTo(PaymentExpirationOutcome.EXPIRED);
        assertThat(orderOf(flow).getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(stockOf(flow)).isEqualTo(STOCK_AFTER_ORDER + ORDER_QUANTITY);
        assertRestored(flow);
    }

    @Test
    @DisplayName("결제 후 회원 취소는 접수(TX1)에서 포인트를 건드리지 않고 PG 환불이 성공한 TX2에서만 되돌린다")
    void memberRefundRestoresPointsOnlyAfterPgSuccess() {
        // given
        Flow flow = paidPaymentWithStock();
        attachPoints(flow, true);

        // when — TX1
        PaymentCancelPreparation preparation = paymentCancelProcessor.prepare(
            flow.memberId(), flow.paymentId(), "point-member-cancel-" + flow.orderId(), CANCEL_REASON);

        // then — 결과를 모르는 동안 포인트는 그대로다
        assertThat(paymentOf(flow).getStatus()).isEqualTo(PaymentStatus.CANCEL_REQUESTED);
        assertUnchangedAfterPayment(flow);

        // when — TX2 성공
        paymentCancelProcessor.apply(
            flow.paymentId(), preparation.refundId(), succeeded("cancel_member_" + flow.orderId()));

        // then
        assertThat(orderOf(flow).getStatus()).isEqualTo(OrderStatus.REFUNDED);
        assertThat(stockOf(flow)).isEqualTo(STOCK_AFTER_ORDER + ORDER_QUANTITY);
        assertRestored(flow);
    }

    @Test
    @DisplayName("마트 거절은 접수(TX1)에서 포인트를 건드리지 않고 PG 환불이 성공한 TX2에서만 되돌린다")
    void martRejectionRestoresPointsOnlyAfterPgSuccess() {
        // given
        Flow flow = paidPaymentWithStock();
        attachPoints(flow, true);
        Long martId = orderOf(flow).getMartId();

        // when — TX1
        MartRejectionPreparation preparation = martRejectionProcessor.prepare(AdminRole.MART_ADMIN, martId, ADMIN_ID,
            flow.orderId(), "point-mart-reject-" + flow.orderId(), REJECT_REASON);

        // then — 결과를 모르는 동안 포인트는 그대로다
        assertThat(paymentOf(flow).getStatus()).isEqualTo(PaymentStatus.CANCEL_REQUESTED);
        assertUnchangedAfterPayment(flow);

        // when — TX2 성공
        martRejectionProcessor.apply(
            flow.paymentId(), preparation.refundId(), succeeded("cancel_admin_" + flow.orderId()));

        // then
        assertThat(orderOf(flow).getStatus()).isEqualTo(OrderStatus.REFUNDED);
        assertThat(stockOf(flow)).isEqualTo(STOCK_AFTER_ORDER + ORDER_QUANTITY);
        assertRestored(flow);
    }

    @Test
    @DisplayName("배송완료로 적립이 확정된 주문을 콘솔에서 전액 취소하면 결제·주문은 정합화하되 포인트는 자동으로 회수하지 않는다")
    void externalCancelKeepsFinalizedEarning() {
        // given — 결제 후 배송까지 끝나 적립이 확정됐다
        Flow flow = paidPaymentWithStock(MartStatus.DELIVERED);
        attachPoints(flow, true);
        transactionTemplate.executeWithoutResult(status -> pointCommandService.confirmEarning(flow.orderId()));
        long balanceBefore = balanceOf(flow);
        LocalDateTime now = LocalDateTime.now(clock).withNano(0);
        PortOneCancellation cancellation = new PortOneCancellation("cancel_console_" + flow.orderId(),
            PortOneCancellationStatus.SUCCEEDED, AMOUNT, now.minusMinutes(1), now, PortOneCancellationTrigger.CONSOLE);

        // when
        ExternalCancellationResult result = externalCancellationProcessor.apply(flow.paymentId(), cancellation);

        // then — PG 사실대로 결제·주문은 닫히고, 포인트는 사용 확정·적립 확정 그대로다
        assertThat(result.outcome()).isEqualTo(ExternalCancellationOutcome.RECONCILED);
        assertThat(paymentOf(flow).getStatus()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(orderOf(flow).getStatus()).isEqualTo(OrderStatus.REFUNDED);
        assertThat(usageStatus(flow)).isEqualTo(PointUsageStatus.CONFIRMED);
        assertThat(earningStatus(flow)).isEqualTo(PointEarningStatus.COMPLETED);
        assertThat(balanceOf(flow)).isEqualTo(balanceBefore);
        assertThat(historyCount(flow, PointHistoryType.RESTORED)).isZero();
        assertThat(historyCount(flow, PointHistoryType.EARN_CANCELED)).isZero();
    }

    // 4단계 주문 생성 연동이 할 일을 흉내 낸다: 1,000P(1년 뒤 만료)를 가진 회원이 이 주문에서 300P를 예약하고 적립 예정을 만든다.
    // 결제가 끝난 픽스처면 결제 성공이 했을 사용 확정까지 마친다
    private void attachPoints(Flow flow, boolean paid) {
        pointMemberIds.add(flow.memberId());
        transactionTemplate.executeWithoutResult(status -> {
            PointEarning lot = PointEarning.pending(flow.memberId(), LOT_ORDER_OFFSET + flow.orderId(), HELD_POINT);
            lot.complete(LocalDateTime.now(clock).minusDays(1));
            pointEarningRepository.save(lot);
            memberPointRepository.save(MemberPoint.builder().memberId(flow.memberId()).balance(HELD_POINT).build());
            pointCommandService.reserveUsage(flow.memberId(), flow.orderId(), USED_POINT);
            pointCommandService.createPendingEarning(
                flow.memberId(), flow.orderId(), PRODUCT_AMOUNT, USED_POINT, RATE);
            if (paid) {
                pointCommandService.confirmUsage(flow.orderId());
            }
        });
    }

    private void assertRestored(Flow flow) {
        assertThat(balanceOf(flow)).isEqualTo(HELD_POINT);
        assertThat(usageStatus(flow)).isEqualTo(PointUsageStatus.RESTORED);
        assertThat(earningStatus(flow)).isEqualTo(PointEarningStatus.CANCELED);
        assertThat(historyCount(flow, PointHistoryType.RESTORED)).isEqualTo(1);
        assertThat(historyCount(flow, PointHistoryType.EARN_CANCELED)).isEqualTo(1);
    }

    private void assertUnchangedAfterPayment(Flow flow) {
        assertThat(balanceOf(flow)).isEqualTo(HELD_POINT - USED_POINT);
        assertThat(usageStatus(flow)).isEqualTo(PointUsageStatus.CONFIRMED);
        assertThat(earningStatus(flow)).isEqualTo(PointEarningStatus.PENDING);
        assertThat(historyCount(flow, PointHistoryType.RESTORED)).isZero();
        assertThat(historyCount(flow, PointHistoryType.EARN_CANCELED)).isZero();
    }

    private PortOneCancelResult succeeded(String cancellationId) {
        return new PortOneCancelResult(PortOneCancelOutcome.SUCCEEDED, cancellationId,
            LocalDateTime.now(clock).withNano(0), "cancellation SUCCEEDED");
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
}
