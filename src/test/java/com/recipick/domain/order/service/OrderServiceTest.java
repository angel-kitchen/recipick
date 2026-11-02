package com.recipick.domain.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.recipick.domain.member.entity.enums.AdminRole;
import com.recipick.domain.order.dto.response.OrderAdminDetailResponse;
import com.recipick.domain.order.dto.response.OrderMartStatusResponse;
import com.recipick.domain.order.entity.Order;
import com.recipick.domain.order.entity.OrderItem;
import com.recipick.domain.order.entity.OrderStatusHistory;
import com.recipick.domain.order.entity.enums.CancelReasonCode;
import com.recipick.domain.order.entity.enums.MartStatus;
import com.recipick.domain.order.entity.enums.OrderStatus;
import com.recipick.domain.order.entity.enums.OrderStatusActor;
import com.recipick.domain.order.entity.enums.OrderStatusType;
import com.recipick.domain.order.exception.OrderErrorCode;
import com.recipick.domain.order.repository.OrderItemRepository;
import com.recipick.domain.order.repository.OrderMemberId;
import com.recipick.domain.order.repository.OrderRepository;
import com.recipick.domain.order.repository.OrderStatusHistoryRepository;
import com.recipick.global.audit.AdminAuditAction;
import com.recipick.global.audit.AdminAuditChange;
import com.recipick.global.audit.AdminAuditCommand;
import com.recipick.global.audit.AdminAuditField;
import com.recipick.global.exception.AuthErrorCode;
import com.recipick.global.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final LocalDateTime PAID_AT = LocalDateTime.of(2026, 10, 20, 12, 0);

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private OrderStatusHistoryRepository orderStatusHistoryRepository;

    @Mock
    private com.recipick.global.audit.AdminAuditRecorder auditRecorder;

    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private OrderService orderService;

    @Captor
    private ArgumentCaptor<List<OrderStatusHistory>> historiesCaptor;

    @Captor
    private ArgumentCaptor<OrderStatusHistory> historyCaptor;

    @Captor
    private ArgumentCaptor<AdminAuditCommand> auditCaptor;

    private Order pendingOrder() {
        return Order.builder()
            .memberId(7L)
            .status(OrderStatus.PENDING_PAYMENT)
            .martStatus(MartStatus.NONE)
            .build();
    }

    private Order receivedOrder(Long martId) {
        return Order.builder()
            .memberId(7L)
            .martId(martId)
            .status(OrderStatus.PAID)
            .martStatus(MartStatus.RECEIVED)
            .build();
    }

    @Test
    @DisplayName("주문의 회원 ID를 돌려준다")
    void getMemberIdOf() {
        // given
        given(orderRepository.findMemberIdById(1L)).willReturn(Optional.of(7L));

        // when
        Long memberId = orderService.getMemberIdOf(1L);

        // then
        assertThat(memberId).isEqualTo(7L);
    }

    @Test
    @DisplayName("없는 주문의 회원 ID를 조회하면 주문을 찾을 수 없다는 오류가 난다")
    void getMemberIdOf_orderNotFound() {
        // given
        given(orderRepository.findMemberIdById(1L)).willReturn(Optional.empty());

        // when, then
        assertThatThrownBy(() -> orderService.getMemberIdOf(1L))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(OrderErrorCode.ORDER_NOT_FOUND);
    }

    @Test
    @DisplayName("주문 당시 마트 ID를 돌려준다")
    void getMartIdOf() {
        // given
        given(orderRepository.findMartIdById(1L)).willReturn(Optional.of(3L));

        // when
        Long martId = orderService.getMartIdOf(1L);

        // then
        assertThat(martId).isEqualTo(3L);
    }

    @Test
    @DisplayName("없는 주문의 마트 ID를 조회하면 주문을 찾을 수 없다는 오류가 난다")
    void getMartIdOf_orderNotFound() {
        // given
        given(orderRepository.findMartIdById(1L)).willReturn(Optional.empty());

        // when, then
        assertThatThrownBy(() -> orderService.getMartIdOf(1L))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(OrderErrorCode.ORDER_NOT_FOUND);
    }

    @Test
    @DisplayName("SUPER_ADMIN은 마트와 관계없이 결제 후 주문 상세를 회원·마트 ID, 상품, 상태 변경 이력과 함께 돌려준다")
    void getAdminOrder_superAdmin() {
        // given
        Order order = receivedOrder(3L);
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));
        given(orderItemRepository.findAllByOrderIdOrderByIdAsc(1L)).willReturn(List.of(
            OrderItem.builder().order(order).productId(10L).productName("애호박")
                .unitPrice(2500L).quantity(2).lineAmount(5000L).build()));
        given(orderStatusHistoryRepository.findAllByOrderIdOrderByIdAsc(1L)).willReturn(List.of(
            OrderStatusHistory.builder().order(order).statusType(OrderStatusType.ORDER)
                .fromStatus("PENDING_PAYMENT").toStatus("PAID").actor(OrderStatusActor.SYSTEM).build(),
            OrderStatusHistory.builder().order(order).statusType(OrderStatusType.MART)
                .fromStatus("NONE").toStatus("RECEIVED").actor(OrderStatusActor.SYSTEM).build()));

        // when
        OrderAdminDetailResponse response = orderService.getAdminOrder(AdminRole.SUPER_ADMIN, null, 1L);

        // then
        assertThat(response.memberId()).isEqualTo(7L);
        assertThat(response.martId()).isEqualTo(3L);
        assertThat(response.orderName()).isEqualTo("애호박");
        assertThat(response.items()).extracting("productName", "lineAmount").containsExactly(tuple("애호박", 5000L));
        assertThat(response.histories())
            .extracting("statusType", "fromStatus", "toStatus", "actor")
            .containsExactly(
                tuple(OrderStatusType.ORDER, "PENDING_PAYMENT", "PAID", OrderStatusActor.SYSTEM),
                tuple(OrderStatusType.MART, "NONE", "RECEIVED", OrderStatusActor.SYSTEM));
    }

    @Test
    @DisplayName("MART_ADMIN이 본인 마트 주문 상세를 조회하면 돌려준다")
    void getAdminOrder_martAdmin() {
        // given
        Order order = receivedOrder(3L);
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));
        given(orderItemRepository.findAllByOrderIdOrderByIdAsc(1L)).willReturn(List.of(
            OrderItem.builder().order(order).productId(10L).productName("애호박")
                .unitPrice(2500L).quantity(2).lineAmount(5000L).build()));

        // when
        OrderAdminDetailResponse response = orderService.getAdminOrder(AdminRole.MART_ADMIN, 3L, 1L);

        // then
        assertThat(response.martId()).isEqualTo(3L);
        assertThat(response.histories()).isEmpty();
    }

    @Test
    @DisplayName("MART_ADMIN이 다른 마트 주문 상세를 조회하면 소속 마트 데이터만 처리할 수 있다는 오류가 난다")
    void getAdminOrder_otherMart() {
        // given
        given(orderRepository.findById(1L)).willReturn(Optional.of(receivedOrder(3L)));

        // when, then
        assertThatThrownBy(() -> orderService.getAdminOrder(AdminRole.MART_ADMIN, 4L, 1L))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(AuthErrorCode.MART_ACCESS_DENIED);
        verify(orderItemRepository, never()).findAllByOrderIdOrderByIdAsc(any());
    }

    @Test
    @DisplayName("결제 전 주문 상세를 관리자가 조회하면 주문을 찾을 수 없다는 오류가 난다")
    void getAdminOrder_pendingOrder() {
        // given
        given(orderRepository.findById(1L)).willReturn(Optional.of(pendingOrder()));

        // when, then
        assertThatThrownBy(() -> orderService.getAdminOrder(AdminRole.SUPER_ADMIN, null, 1L))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(OrderErrorCode.ORDER_NOT_FOUND);
    }

    @Test
    @DisplayName("주문 ID 목록의 회원 ID를 주문 ID별로 돌려준다")
    void findMemberIdsOf() {
        // given
        given(orderRepository.findMemberIdsByIdIn(List.of(1L, 2L))).willReturn(List.of(
            orderMemberId(1L, 7L),
            orderMemberId(2L, 8L)));

        // when
        Map<Long, Long> result = orderService.findMemberIdsOf(List.of(1L, 2L));

        // then
        assertThat(result).containsExactlyInAnyOrderEntriesOf(Map.of(1L, 7L, 2L, 8L));
    }

    @Test
    @DisplayName("빈 주문 ID 목록이면 조회하지 않고 빈 결과를 돌려준다")
    void findMemberIdsOf_emptyOrderIds() {
        // when
        Map<Long, Long> result = orderService.findMemberIdsOf(List.of());

        // then
        assertThat(result).isEmpty();
        verify(orderRepository, never()).findMemberIdsByIdIn(any());
    }

    @Test
    @DisplayName("결제 대기 주문에 결제 성공이 오면 주문 상태와 마트 처리 상태 이력을 시스템 주체로 저장하고 장바구니 정리 이벤트를 발행한다")
    void markPaid() {
        // given
        Order order = pendingOrder();
        given(orderRepository.findByIdForUpdate(1L)).willReturn(Optional.of(order));
        given(orderItemRepository.findAllByOrderIdOrderByIdAsc(1L)).willReturn(List.of(
            OrderItem.builder().productId(10L).build(), OrderItem.builder().productId(20L).build()));

        // when
        orderService.markPaid(1L, PAID_AT);

        // then
        verify(orderStatusHistoryRepository).saveAll(historiesCaptor.capture());
        assertThat(historiesCaptor.getValue())
            .extracting(OrderStatusHistory::getStatusType, OrderStatusHistory::getFromStatus,
                OrderStatusHistory::getToStatus, OrderStatusHistory::getActor)
            .containsExactly(
                tuple(OrderStatusType.ORDER, "PENDING_PAYMENT", "PAID", OrderStatusActor.SYSTEM),
                tuple(OrderStatusType.MART, "NONE", "RECEIVED", OrderStatusActor.SYSTEM));
        verify(eventPublisher).publishEvent(
            new com.recipick.domain.order.event.OrderPaidEvent(1L, order.getMemberId(), List.of(10L, 20L)));
    }

    @Test
    @DisplayName("없는 주문에 결제 성공이 오면 주문을 찾을 수 없다는 오류가 난다")
    void markPaid_orderNotFound() {
        // given
        given(orderRepository.findByIdForUpdate(1L)).willReturn(Optional.empty());

        // when, then
        assertThatThrownBy(() -> orderService.markPaid(1L, PAID_AT))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(OrderErrorCode.ORDER_NOT_FOUND);
        verify(orderStatusHistoryRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("이미 결제 완료된 주문에 결제 성공이 다시 오면 상태와 이력을 바꾸지 않는다")
    void markPaid_alreadyPaid() {
        // given
        Order order = pendingOrder();
        order.markPaid(PAID_AT);
        given(orderRepository.findByIdForUpdate(1L)).willReturn(Optional.of(order));

        // when
        orderService.markPaid(1L, PAID_AT.plusMinutes(1));

        // then
        assertThat(order.getPaidAt()).isEqualTo(PAID_AT);
        verify(orderStatusHistoryRepository, never()).saveAll(any());
        // 그 사이 회원이 다시 담은 상품을 지우지 않도록 정리 이벤트도 다시 내지 않는다
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("결제 대기 주문을 결제 실패로 종료하면 사유가 담긴 이력을 남기고 재고를 복구할 주문 상품을 돌려준다")
    void closeByPaymentFailure() {
        // given
        List<OrderItem> items = List.of(OrderItem.builder().productId(10L).quantity(2).build());
        Order order = pendingOrder();
        given(orderRepository.findByIdForUpdate(1L)).willReturn(Optional.of(order));
        given(orderItemRepository.findAllByOrderId(1L)).willReturn(items);

        // when
        List<OrderItem> result = orderService.closeByPaymentFailure(1L, CancelReasonCode.PAYMENT_TIMEOUT, "결제 시간 만료");

        // then
        assertThat(result).isEqualTo(items);
        assertThat(order.getCancelReasonDetail()).isEqualTo("결제 시간 만료");
        verify(orderStatusHistoryRepository).save(historyCaptor.capture());
        assertThat(historyCaptor.getValue())
            .extracting(OrderStatusHistory::getStatusType, OrderStatusHistory::getFromStatus,
                OrderStatusHistory::getToStatus, OrderStatusHistory::getActor, OrderStatusHistory::getReason)
            .containsExactly(OrderStatusType.ORDER, "PENDING_PAYMENT", "CANCELED", OrderStatusActor.SYSTEM,
                "PAYMENT_TIMEOUT");
    }

    @Test
    @DisplayName("이미 취소된 주문에 결제 실패가 다시 오면 최초 사유를 유지하고 복구할 상품 없이 끝난다")
    void closeByPaymentFailure_alreadyCanceled() {
        // given
        Order order = pendingOrder();
        order.cancel(CancelReasonCode.PAYMENT_TIMEOUT, "결제 시간 만료");
        given(orderRepository.findByIdForUpdate(1L)).willReturn(Optional.of(order));

        // when
        List<OrderItem> result = orderService.closeByPaymentFailure(1L, CancelReasonCode.PAYMENT_FAILED, "카드 한도 초과");

        // then
        assertThat(result).isEmpty();
        assertThat(order.getCancelReasonCode()).isEqualTo(CancelReasonCode.PAYMENT_TIMEOUT);
        assertThat(order.getCancelReasonDetail()).isEqualTo("결제 시간 만료");
        verify(orderStatusHistoryRepository, never()).save(any());
        verify(orderItemRepository, never()).findAllByOrderId(any());
    }

    @Test
    @DisplayName("결제 전 회원 취소면 회원 취소 사유로 주문을 종료하고 재고를 복구할 주문 상품을 돌려준다")
    void closeByPaymentFailure_memberCanceledBeforePay() {
        // given
        List<OrderItem> items = List.of(OrderItem.builder().productId(10L).quantity(2).build());
        Order order = pendingOrder();
        given(orderRepository.findByIdForUpdate(1L)).willReturn(Optional.of(order));
        given(orderItemRepository.findAllByOrderId(1L)).willReturn(items);

        // when
        List<OrderItem> result = orderService.closeByPaymentFailure(1L, CancelReasonCode.MEMBER_CANCELED, null);

        // then
        assertThat(result).isEqualTo(items);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(order.getCancelReasonCode()).isEqualTo(CancelReasonCode.MEMBER_CANCELED);
        verify(orderStatusHistoryRepository).save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getReason()).isEqualTo("MEMBER_CANCELED");
    }

    @ParameterizedTest
    @NullSource
    @EnumSource(value = CancelReasonCode.class, names = "MART_REJECTED")
    @DisplayName("결제 전 종료 사유가 아니면 주문을 잠그지 않고 거부한다")
    void closeByPaymentFailure_notPaymentFailureReason(CancelReasonCode reasonCode) {
        // when, then
        assertThatThrownBy(() -> orderService.closeByPaymentFailure(1L, reasonCode, null))
            .isInstanceOf(IllegalArgumentException.class);
        verify(orderRepository, never()).findByIdForUpdate(any());
    }

    @Test
    @DisplayName("없는 주문에 결제 실패가 오면 주문을 찾을 수 없다는 오류가 난다")
    void closeByPaymentFailure_orderNotFound() {
        // given
        given(orderRepository.findByIdForUpdate(1L)).willReturn(Optional.empty());

        // when, then
        assertThatThrownBy(() -> orderService.closeByPaymentFailure(1L, CancelReasonCode.PAYMENT_FAILED, null))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(OrderErrorCode.ORDER_NOT_FOUND);
    }

    private Order paidOrder(MartStatus martStatus) {
        return Order.builder()
            .memberId(7L)
            .status(OrderStatus.PAID)
            .martStatus(martStatus)
            .build();
    }

    // 배송 상태 변경은 마트 범위를 보므로 마트 ID를 넣는다
    private Order deliveryOrder(OrderStatus status, MartStatus martStatus) {
        return Order.builder()
            .memberId(7L)
            .martId(3L)
            .status(status)
            .martStatus(martStatus)
            .build();
    }

    private Order memberCancelRequestedOrder() {
        Order order = paidOrder(MartStatus.RECEIVED);
        order.requestMemberCancel("단순 변심");
        return order;
    }

    @Test
    @DisplayName("접수중인 결제 완료 주문을 수락하면 배송준비중으로 바꾸고 처리 관리자가 담긴 마트 주체 이력을 남긴다")
    void acceptByMart() {
        // given
        Order order = receivedOrder(3L);
        given(orderRepository.findByIdForUpdate(1L)).willReturn(Optional.of(order));

        // when
        OrderMartStatusResponse response = orderService.acceptByMart(AdminRole.MART_ADMIN, 3L, 20L, 1L);

        // then
        assertThat(response.status()).isEqualTo(OrderStatus.PAID);
        assertThat(response.martStatus()).isEqualTo(MartStatus.PREPARING);
        assertThat(order.getMartStatus()).isEqualTo(MartStatus.PREPARING);
        verify(orderStatusHistoryRepository).save(historyCaptor.capture());
        OrderStatusHistory history = historyCaptor.getValue();
        assertThat(history.getStatusType()).isEqualTo(OrderStatusType.MART);
        assertThat(history.getFromStatus()).isEqualTo("RECEIVED");
        assertThat(history.getToStatus()).isEqualTo("PREPARING");
        assertThat(history.getActor()).isEqualTo(OrderStatusActor.MART);
        assertThat(history.getChangedByAdminId()).isEqualTo(20L);
        assertThat(history.getReason()).isNull();
    }

    @Test
    @DisplayName("이미 수락된 주문에 수락이 다시 오면 이력을 남기지 않고 현재 상태를 돌려준다")
    void acceptByMart_alreadyPreparing() {
        // given
        Order order = receivedOrder(3L);
        order.acceptByMart();
        given(orderRepository.findByIdForUpdate(1L)).willReturn(Optional.of(order));

        // when
        OrderMartStatusResponse response = orderService.acceptByMart(AdminRole.SUPER_ADMIN, null, 10L, 1L);

        // then
        assertThat(response.martStatus()).isEqualTo(MartStatus.PREPARING);
        verify(orderStatusHistoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("MART_ADMIN이 다른 마트 주문을 수락하면 소속 마트 데이터만 처리할 수 있다는 오류가 나고 상태는 그대로다")
    void acceptByMart_otherMart() {
        // given
        Order order = receivedOrder(3L);
        given(orderRepository.findByIdForUpdate(1L)).willReturn(Optional.of(order));

        // when, then
        assertThatThrownBy(() -> orderService.acceptByMart(AdminRole.MART_ADMIN, 4L, 20L, 1L))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(AuthErrorCode.MART_ACCESS_DENIED);
        assertThat(order.getMartStatus()).isEqualTo(MartStatus.RECEIVED);
        verify(orderStatusHistoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("회원 취소가 진행 중인 주문을 수락하면 수락할 수 없다는 오류가 난다")
    void acceptByMart_cancelRequested() {
        // given
        Order order = receivedOrder(3L);
        order.requestMemberCancel("단순 변심");
        given(orderRepository.findByIdForUpdate(1L)).willReturn(Optional.of(order));

        // when, then
        assertThatThrownBy(() -> orderService.acceptByMart(AdminRole.MART_ADMIN, 3L, 20L, 1L))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(OrderErrorCode.ORDER_NOT_ACCEPTABLE);
        verify(orderStatusHistoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("결제 전 주문을 수락하면 주문을 찾을 수 없다는 오류가 난다")
    void acceptByMart_pendingOrder() {
        // given
        given(orderRepository.findByIdForUpdate(1L)).willReturn(Optional.of(pendingOrder()));

        // when, then
        assertThatThrownBy(() -> orderService.acceptByMart(AdminRole.SUPER_ADMIN, null, 10L, 1L))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(OrderErrorCode.ORDER_NOT_FOUND);
    }

    @Test
    @DisplayName("MART_ADMIN이 본인 마트의 배송준비중 주문을 배송중으로 바꾸면 처리 관리자가 담긴 마트 주체 이력과 배송 상태 변경 감사를 남긴다")
    void changeDeliveryStatus_shipping() {
        // given
        Order order = deliveryOrder(OrderStatus.PAID, MartStatus.PREPARING);
        given(orderRepository.findByIdForUpdate(1L)).willReturn(Optional.of(order));

        // when
        OrderMartStatusResponse response =
            orderService.changeDeliveryStatus(AdminRole.MART_ADMIN, 3L, 20L, 1L, MartStatus.SHIPPING);

        // then
        assertThat(response.status()).isEqualTo(OrderStatus.PAID);
        assertThat(response.martStatus()).isEqualTo(MartStatus.SHIPPING);
        verify(orderStatusHistoryRepository).save(historyCaptor.capture());
        OrderStatusHistory history = historyCaptor.getValue();
        assertThat(history.getStatusType()).isEqualTo(OrderStatusType.MART);
        assertThat(history.getFromStatus()).isEqualTo("PREPARING");
        assertThat(history.getToStatus()).isEqualTo("SHIPPING");
        assertThat(history.getActor()).isEqualTo(OrderStatusActor.MART);
        assertThat(history.getChangedByAdminId()).isEqualTo(20L);
        assertThat(history.getReason()).isNull();
        verify(auditRecorder).record(auditCaptor.capture());
        AdminAuditCommand audit = auditCaptor.getValue();
        assertThat(audit.action()).isEqualTo(AdminAuditAction.ORDER_DELIVERY_CHANGE);
        assertThat(audit.targetId()).isEqualTo(1L);
        assertThat(audit.martId()).isEqualTo(3L);
        assertThat(audit.changes()).containsExactly(Map.entry(AdminAuditField.STATUS,
            new AdminAuditChange(MartStatus.PREPARING, MartStatus.SHIPPING)));
    }

    @Test
    @DisplayName("SUPER_ADMIN이 배송중 주문을 배송완료로 바꾸면 감사의 마트는 주문의 마트로 남는다")
    void changeDeliveryStatus_deliveredBySuperAdmin() {
        // given
        Order order = deliveryOrder(OrderStatus.PAID, MartStatus.SHIPPING);
        given(orderRepository.findByIdForUpdate(1L)).willReturn(Optional.of(order));

        // when
        OrderMartStatusResponse response =
            orderService.changeDeliveryStatus(AdminRole.SUPER_ADMIN, null, 10L, 1L, MartStatus.DELIVERED);

        // then
        assertThat(response.martStatus()).isEqualTo(MartStatus.DELIVERED);
        verify(orderStatusHistoryRepository).save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getFromStatus()).isEqualTo("SHIPPING");
        assertThat(historyCaptor.getValue().getToStatus()).isEqualTo("DELIVERED");
        assertThat(historyCaptor.getValue().getChangedByAdminId()).isEqualTo(10L);
        verify(auditRecorder).record(auditCaptor.capture());
        assertThat(auditCaptor.getValue().martId()).isEqualTo(3L);
        assertThat(auditCaptor.getValue().changes()).containsExactly(Map.entry(AdminAuditField.STATUS,
            new AdminAuditChange(MartStatus.SHIPPING, MartStatus.DELIVERED)));
    }

    @ParameterizedTest
    @EnumSource(value = MartStatus.class, names = {"SHIPPING", "DELIVERED"})
    @DisplayName("이미 같은 배송 상태인 주문에 같은 요청이 다시 오면 이력·감사 없이 현재 상태를 돌려준다")
    void changeDeliveryStatus_sameTarget(MartStatus martStatus) {
        // given
        Order order = deliveryOrder(OrderStatus.PAID, martStatus);
        given(orderRepository.findByIdForUpdate(1L)).willReturn(Optional.of(order));

        // when
        OrderMartStatusResponse response =
            orderService.changeDeliveryStatus(AdminRole.MART_ADMIN, 3L, 20L, 1L, martStatus);

        // then
        assertThat(response.martStatus()).isEqualTo(martStatus);
        verify(orderStatusHistoryRepository, never()).save(any());
        verifyNoInteractions(auditRecorder);
    }

    @ParameterizedTest
    @CsvSource({"PREPARING, DELIVERED", "DELIVERED, SHIPPING", "RECEIVED, SHIPPING"})
    @DisplayName("배송 단계를 건너뛰거나 되돌리면 ORDER-017이고 상태·이력·감사는 그대로다")
    void changeDeliveryStatus_invalidTransition(MartStatus from, MartStatus to) {
        // given
        Order order = deliveryOrder(OrderStatus.PAID, from);
        given(orderRepository.findByIdForUpdate(1L)).willReturn(Optional.of(order));

        // when, then
        assertThatThrownBy(() -> orderService.changeDeliveryStatus(AdminRole.MART_ADMIN, 3L, 20L, 1L, to))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(OrderErrorCode.INVALID_MART_STATUS_TRANSITION);
        assertThat(order.getMartStatus()).isEqualTo(from);
        verify(orderStatusHistoryRepository, never()).save(any());
        verifyNoInteractions(auditRecorder);
    }

    @ParameterizedTest
    @EnumSource(value = MartStatus.class, names = {"NONE", "RECEIVED", "PREPARING", "REJECTED"})
    @DisplayName("배송중·배송완료가 아닌 목표는 현재 상태와 같아도 성공으로 답하지 않고 ORDER-017이다")
    void changeDeliveryStatus_notDeliveryTarget(MartStatus target) {
        // given
        Order order = deliveryOrder(OrderStatus.PAID, MartStatus.PREPARING);
        given(orderRepository.findByIdForUpdate(1L)).willReturn(Optional.of(order));

        // when, then
        assertThatThrownBy(() -> orderService.changeDeliveryStatus(AdminRole.MART_ADMIN, 3L, 20L, 1L, target))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(OrderErrorCode.INVALID_MART_STATUS_TRANSITION);
        assertThat(order.getMartStatus()).isEqualTo(MartStatus.PREPARING);
        verify(orderStatusHistoryRepository, never()).save(any());
        verifyNoInteractions(auditRecorder);
    }

    @Test
    @DisplayName("환불로 끝난 배송완료 주문에 배송완료가 다시 오면 같은 상태라도 성공으로 숨기지 않고 ORDER-016이다")
    void changeDeliveryStatus_refundedDelivered() {
        // given
        Order order = deliveryOrder(OrderStatus.REFUNDED, MartStatus.DELIVERED);
        given(orderRepository.findByIdForUpdate(1L)).willReturn(Optional.of(order));

        // when, then
        assertThatThrownBy(() -> orderService.changeDeliveryStatus(
            AdminRole.MART_ADMIN, 3L, 20L, 1L, MartStatus.DELIVERED))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(OrderErrorCode.INVALID_ORDER_STATUS_TRANSITION);
        verify(orderStatusHistoryRepository, never()).save(any());
        verifyNoInteractions(auditRecorder);
    }

    @Test
    @DisplayName("회원 취소가 진행 중인 주문은 배송 상태를 바꿀 수 없어 ORDER-016이다")
    void changeDeliveryStatus_cancelRequested() {
        // given
        Order order = deliveryOrder(OrderStatus.PAID, MartStatus.RECEIVED);
        order.requestMemberCancel("단순 변심");
        given(orderRepository.findByIdForUpdate(1L)).willReturn(Optional.of(order));

        // when, then
        assertThatThrownBy(() -> orderService.changeDeliveryStatus(
            AdminRole.MART_ADMIN, 3L, 20L, 1L, MartStatus.SHIPPING))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(OrderErrorCode.INVALID_ORDER_STATUS_TRANSITION);
        assertThat(order.getMartStatus()).isEqualTo(MartStatus.RECEIVED);
        verify(orderStatusHistoryRepository, never()).save(any());
        verifyNoInteractions(auditRecorder);
    }

    @Test
    @DisplayName("MART_ADMIN이 다른 마트 주문의 배송 상태를 바꾸면 AUTH-005이고 상태는 그대로다")
    void changeDeliveryStatus_otherMart() {
        // given
        Order order = deliveryOrder(OrderStatus.PAID, MartStatus.PREPARING);
        given(orderRepository.findByIdForUpdate(1L)).willReturn(Optional.of(order));

        // when, then
        assertThatThrownBy(() -> orderService.changeDeliveryStatus(
            AdminRole.MART_ADMIN, 4L, 20L, 1L, MartStatus.SHIPPING))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(AuthErrorCode.MART_ACCESS_DENIED);
        assertThat(order.getMartStatus()).isEqualTo(MartStatus.PREPARING);
        verify(orderStatusHistoryRepository, never()).save(any());
        verifyNoInteractions(auditRecorder);
    }

    @Test
    @DisplayName("결제 전 주문의 배송 상태를 바꾸면 없는 주문과 같이 ORDER-012이다")
    void changeDeliveryStatus_pendingOrder() {
        // given
        given(orderRepository.findByIdForUpdate(1L)).willReturn(Optional.of(pendingOrder()));

        // when, then
        assertThatThrownBy(() -> orderService.changeDeliveryStatus(
            AdminRole.SUPER_ADMIN, null, 10L, 1L, MartStatus.SHIPPING))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(OrderErrorCode.ORDER_NOT_FOUND);
        verifyNoInteractions(auditRecorder);
    }

    @Test
    @DisplayName("없는 주문의 배송 상태를 바꾸면 ORDER-012이다")
    void changeDeliveryStatus_orderNotFound() {
        // given
        given(orderRepository.findByIdForUpdate(1L)).willReturn(Optional.empty());

        // when, then
        assertThatThrownBy(() -> orderService.changeDeliveryStatus(
            AdminRole.SUPER_ADMIN, null, 10L, 1L, MartStatus.SHIPPING))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(OrderErrorCode.ORDER_NOT_FOUND);
    }

    @Test
    @DisplayName("접수중인 결제 완료 주문을 마트가 거절하면 취소 요청·마트 거절 상태로 바꾸고 처리 관리자가 담긴 마트 주체 이력을 두 건 남긴다")
    void requestMartRejection() {
        // given
        Order order = receivedOrder(3L);
        given(orderRepository.findByIdForUpdate(1L)).willReturn(Optional.of(order));

        // when
        MartRejectRequestResult result = orderService.requestMartRejection(AdminRole.MART_ADMIN, 3L, 20L, 1L, "재료 소진");

        // then
        assertThat(result).isEqualTo(MartRejectRequestResult.REJECTED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCEL_REQUESTED);
        assertThat(order.getMartStatus()).isEqualTo(MartStatus.REJECTED);
        assertThat(order.getCancelReasonCode()).isEqualTo(CancelReasonCode.MART_REJECTED);
        assertThat(order.getCancelReasonDetail()).isEqualTo("재료 소진");
        verify(orderStatusHistoryRepository).saveAll(historiesCaptor.capture());
        assertThat(historiesCaptor.getValue())
            .extracting(OrderStatusHistory::getStatusType, OrderStatusHistory::getFromStatus,
                OrderStatusHistory::getToStatus, OrderStatusHistory::getActor,
                OrderStatusHistory::getChangedByAdminId, OrderStatusHistory::getReason)
            .containsExactly(
                tuple(OrderStatusType.ORDER, "PAID", "CANCEL_REQUESTED", OrderStatusActor.MART, 20L,
                    "MART_REJECTED"),
                tuple(OrderStatusType.MART, "RECEIVED", "REJECTED", OrderStatusActor.MART, 20L, "재료 소진"));
    }

    @Test
    @DisplayName("SUPER_ADMIN이 거절해도 주체는 마트이고 처리 관리자 ID로 누가 처리했는지 남긴다")
    void requestMartRejection_superAdmin() {
        // given
        Order order = receivedOrder(3L);
        given(orderRepository.findByIdForUpdate(1L)).willReturn(Optional.of(order));

        // when
        MartRejectRequestResult result =
            orderService.requestMartRejection(AdminRole.SUPER_ADMIN, null, 10L, 1L, "재료 소진");

        // then
        assertThat(result).isEqualTo(MartRejectRequestResult.REJECTED);
        verify(orderStatusHistoryRepository).saveAll(historiesCaptor.capture());
        assertThat(historiesCaptor.getValue())
            .extracting(OrderStatusHistory::getActor, OrderStatusHistory::getChangedByAdminId)
            .containsOnly(tuple(OrderStatusActor.MART, 10L));
    }

    @Test
    @DisplayName("MART_ADMIN이 다른 마트 주문을 거절하면 소속 마트 데이터만 처리할 수 있다는 오류가 나고 상태는 그대로다")
    void requestMartRejection_otherMart() {
        // given
        Order order = receivedOrder(3L);
        given(orderRepository.findByIdForUpdate(1L)).willReturn(Optional.of(order));

        // when, then
        assertThatThrownBy(() -> orderService.requestMartRejection(AdminRole.MART_ADMIN, 4L, 20L, 1L, "재료 소진"))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(AuthErrorCode.MART_ACCESS_DENIED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getMartStatus()).isEqualTo(MartStatus.RECEIVED);
        verify(orderStatusHistoryRepository, never()).saveAll(any());
    }

    @ParameterizedTest
    @EnumSource(value = MartStatus.class, names = {"PREPARING", "SHIPPING", "DELIVERED"})
    @DisplayName("마트가 준비를 시작한 주문은 상태를 바꾸지 않고 거절할 수 없다고 돌려준다")
    void requestMartRejection_martAlreadyPreparing(MartStatus martStatus) {
        // given
        Order order = paidOrder(martStatus);
        given(orderRepository.findByIdForUpdate(1L)).willReturn(Optional.of(order));

        // when
        MartRejectRequestResult result =
            orderService.requestMartRejection(AdminRole.SUPER_ADMIN, null, 10L, 1L, "재료 소진");

        // then
        assertThat(result).isEqualTo(MartRejectRequestResult.NOT_REJECTABLE);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getMartStatus()).isEqualTo(martStatus);
        verify(orderStatusHistoryRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("회원 취소가 진행 중인 주문은 회원 취소 사유를 그대로 두고 거절할 수 없다고 돌려준다")
    void requestMartRejection_memberCancelRequested() {
        // given
        Order order = memberCancelRequestedOrder();
        given(orderRepository.findByIdForUpdate(1L)).willReturn(Optional.of(order));

        // when
        MartRejectRequestResult result =
            orderService.requestMartRejection(AdminRole.SUPER_ADMIN, null, 10L, 1L, "재료 소진");

        // then
        assertThat(result).isEqualTo(MartRejectRequestResult.NOT_REJECTABLE);
        assertThat(order.getCancelReasonCode()).isEqualTo(CancelReasonCode.MEMBER_CANCELED);
        assertThat(order.getCancelReasonDetail()).isEqualTo("단순 변심");
        verify(orderStatusHistoryRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("이미 거절 접수된 주문에 거절이 다시 오면 최초 거절 사유를 그대로 두고 이미 거절됐다고 돌려준다")
    void requestMartRejection_alreadyRejected() {
        // given
        Order order = receivedOrder(3L);
        order.rejectByMart("재료 소진");
        given(orderRepository.findByIdForUpdate(1L)).willReturn(Optional.of(order));

        // when
        MartRejectRequestResult result = orderService.requestMartRejection(AdminRole.MART_ADMIN, 3L, 20L, 1L, "다른 사유");

        // then
        assertThat(result).isEqualTo(MartRejectRequestResult.ALREADY_REJECTED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCEL_REQUESTED);
        assertThat(order.getCancelReasonDetail()).isEqualTo("재료 소진");
        verify(orderStatusHistoryRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("거절 환불까지 끝난 주문에 거절이 다시 와도 이미 거절됐다고 돌려준다")
    void requestMartRejection_refundedAfterRejection() {
        // given
        Order order = receivedOrder(3L);
        order.rejectByMart("재료 소진");
        order.markRefunded();
        given(orderRepository.findByIdForUpdate(1L)).willReturn(Optional.of(order));

        // when
        MartRejectRequestResult result = orderService.requestMartRejection(AdminRole.MART_ADMIN, 3L, 20L, 1L, "재료 소진");

        // then
        assertThat(result).isEqualTo(MartRejectRequestResult.ALREADY_REJECTED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUNDED);
        verify(orderStatusHistoryRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("결제 전 주문을 거절하면 주문을 찾을 수 없다는 오류가 난다")
    void requestMartRejection_pendingOrder() {
        // given
        given(orderRepository.findByIdForUpdate(1L)).willReturn(Optional.of(pendingOrder()));

        // when, then
        assertThatThrownBy(() -> orderService.requestMartRejection(AdminRole.SUPER_ADMIN, null, 10L, 1L, "재료 소진"))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(OrderErrorCode.ORDER_NOT_FOUND);
    }

    @Test
    @DisplayName("없는 주문을 거절하면 주문을 찾을 수 없다는 오류가 난다")
    void requestMartRejection_orderNotFound() {
        // given
        given(orderRepository.findByIdForUpdate(1L)).willReturn(Optional.empty());

        // when, then
        assertThatThrownBy(() -> orderService.requestMartRejection(AdminRole.SUPER_ADMIN, null, 10L, 1L, "재료 소진"))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(OrderErrorCode.ORDER_NOT_FOUND);
    }

    @Test
    @DisplayName("접수중인 결제 완료 주문에 회원 취소를 요청하면 취소 요청 상태로 바꾸고 회원 주체 이력을 남긴다")
    void markMemberCancelRequested() {
        // given
        Order order = paidOrder(MartStatus.RECEIVED);
        given(orderRepository.findByIdForUpdate(1L)).willReturn(Optional.of(order));

        // when
        MemberCancelRequestResult result = orderService.markMemberCancelRequested(1L, "단순 변심");

        // then
        assertThat(result).isEqualTo(MemberCancelRequestResult.REQUESTED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCEL_REQUESTED);
        assertThat(order.getCancelReasonDetail()).isEqualTo("단순 변심");
        verify(orderStatusHistoryRepository).save(historyCaptor.capture());
        assertThat(historyCaptor.getValue())
            .extracting(OrderStatusHistory::getStatusType, OrderStatusHistory::getFromStatus,
                OrderStatusHistory::getToStatus, OrderStatusHistory::getActor, OrderStatusHistory::getReason)
            .containsExactly(OrderStatusType.ORDER, "PAID", "CANCEL_REQUESTED", OrderStatusActor.MEMBER,
                "MEMBER_CANCELED");
    }

    @ParameterizedTest
    @EnumSource(value = MartStatus.class, names = {"PREPARING", "SHIPPING", "DELIVERED"})
    @DisplayName("마트가 준비를 시작한 주문은 상태를 바꾸지 않고 마트가 이미 준비 중이라고 돌려준다")
    void markMemberCancelRequested_martAlreadyPreparing(MartStatus martStatus) {
        // given
        Order order = paidOrder(martStatus);
        given(orderRepository.findByIdForUpdate(1L)).willReturn(Optional.of(order));

        // when
        MemberCancelRequestResult result = orderService.markMemberCancelRequested(1L, null);

        // then
        assertThat(result).isEqualTo(MemberCancelRequestResult.MART_ALREADY_PREPARING);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        verify(orderStatusHistoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("이미 취소 요청 중인 주문에 회원 취소가 다시 오면 상태와 사유를 바꾸지 않고 잘못된 상태라고 돌려준다")
    void markMemberCancelRequested_alreadyCancelRequested() {
        // given
        Order order = memberCancelRequestedOrder();
        given(orderRepository.findByIdForUpdate(1L)).willReturn(Optional.of(order));

        // when
        MemberCancelRequestResult result = orderService.markMemberCancelRequested(1L, "다른 사유");

        // then
        assertThat(result).isEqualTo(MemberCancelRequestResult.INVALID_ORDER_STATUS);
        assertThat(order.getCancelReasonDetail()).isEqualTo("단순 변심");
        verify(orderStatusHistoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("회원 취소가 실패하면 결제 완료로 되돌리고 시스템 주체 이력을 남긴다")
    void restorePaidAfterMemberCancelFailure() {
        // given
        Order order = memberCancelRequestedOrder();
        given(orderRepository.findByIdForUpdate(1L)).willReturn(Optional.of(order));

        // when
        orderService.restorePaidAfterMemberCancelFailure(1L);

        // then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        verify(orderStatusHistoryRepository).save(historyCaptor.capture());
        assertThat(historyCaptor.getValue())
            .extracting(OrderStatusHistory::getFromStatus, OrderStatusHistory::getToStatus,
                OrderStatusHistory::getActor)
            .containsExactly("CANCEL_REQUESTED", "PAID", OrderStatusActor.SYSTEM);
    }

    @Test
    @DisplayName("이미 결제 완료로 되돌린 주문에 취소 실패가 다시 오면 아무것도 바꾸지 않는다")
    void restorePaidAfterMemberCancelFailure_alreadyPaid() {
        // given
        given(orderRepository.findByIdForUpdate(1L)).willReturn(Optional.of(paidOrder(MartStatus.RECEIVED)));

        // when
        orderService.restorePaidAfterMemberCancelFailure(1L);

        // then
        verify(orderStatusHistoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("취소 요청 주문을 환불 완료로 바꾸면 취소 사유가 담긴 이력을 남기고 재고를 복구할 주문 상품을 돌려준다")
    void markRefunded() {
        // given
        List<OrderItem> items = List.of(OrderItem.builder().productId(10L).quantity(2).build());
        Order order = memberCancelRequestedOrder();
        given(orderRepository.findByIdForUpdate(1L)).willReturn(Optional.of(order));
        given(orderItemRepository.findAllByOrderId(1L)).willReturn(items);

        // when
        List<OrderItem> result = orderService.markRefunded(1L);

        // then
        assertThat(result).isEqualTo(items);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUNDED);
        verify(orderStatusHistoryRepository).save(historyCaptor.capture());
        assertThat(historyCaptor.getValue())
            .extracting(OrderStatusHistory::getFromStatus, OrderStatusHistory::getToStatus,
                OrderStatusHistory::getActor, OrderStatusHistory::getReason)
            .containsExactly("CANCEL_REQUESTED", "REFUNDED", OrderStatusActor.SYSTEM, "MEMBER_CANCELED");
    }

    @Test
    @DisplayName("이미 환불 완료된 주문에 환불 성공이 다시 오면 복구할 상품 없이 끝난다")
    void markRefunded_alreadyRefunded() {
        // given
        Order order = memberCancelRequestedOrder();
        order.markRefunded();
        given(orderRepository.findByIdForUpdate(1L)).willReturn(Optional.of(order));

        // when
        List<OrderItem> result = orderService.markRefunded(1L);

        // then
        assertThat(result).isEmpty();
        verify(orderStatusHistoryRepository, never()).save(any());
        verify(orderItemRepository, never()).findAllByOrderId(any());
    }

    @ParameterizedTest
    @EnumSource(value = MartStatus.class, names = {"RECEIVED", "PREPARING"})
    @DisplayName("상품이 아직 마트에 있는 결제 완료 주문을 외부 취소로 종료하면 외부 취소 사유가 담긴 이력을 남기고 재고를 복구할 주문 상품을 돌려준다")
    void closeByExternalCancel_stockStillInMart(MartStatus martStatus) {
        // given
        List<OrderItem> items = List.of(OrderItem.builder().productId(10L).quantity(2).build());
        Order order = paidOrder(martStatus);
        given(orderRepository.findByIdForUpdate(1L)).willReturn(Optional.of(order));
        given(orderItemRepository.findAllByOrderId(1L)).willReturn(items);

        // when
        List<OrderItem> result = orderService.closeByExternalCancel(1L, "PortOne 콘솔 전액 취소");

        // then
        assertThat(result).isEqualTo(items);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUNDED);
        assertThat(order.getMartStatus()).isEqualTo(martStatus);
        verify(orderStatusHistoryRepository).save(historyCaptor.capture());
        assertThat(historyCaptor.getValue())
            .extracting(OrderStatusHistory::getFromStatus, OrderStatusHistory::getToStatus,
                OrderStatusHistory::getActor, OrderStatusHistory::getReason)
            .containsExactly("PAID", "REFUNDED", OrderStatusActor.SYSTEM, "EXTERNAL_CANCELED");
    }

    @ParameterizedTest
    @EnumSource(value = MartStatus.class, names = {"SHIPPING", "DELIVERED"})
    @DisplayName("배송이 시작된 결제 완료 주문을 외부 취소로 종료하면 이력은 남기지만 복구할 상품 없이 끝난다")
    void closeByExternalCancel_shipped(MartStatus martStatus) {
        // given
        Order order = paidOrder(martStatus);
        given(orderRepository.findByIdForUpdate(1L)).willReturn(Optional.of(order));

        // when
        List<OrderItem> result = orderService.closeByExternalCancel(1L, null);

        // then
        assertThat(result).isEmpty();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUNDED);
        verify(orderStatusHistoryRepository).save(any());
        verify(orderItemRepository, never()).findAllByOrderId(any());
    }

    @Test
    @DisplayName("이미 환불 완료된 주문에 외부 취소가 다시 오면 상태와 사유를 바꾸지 않고 복구할 상품 없이 끝난다")
    void closeByExternalCancel_alreadyRefunded() {
        // given
        Order order = memberCancelRequestedOrder();
        order.markRefunded();
        given(orderRepository.findByIdForUpdate(1L)).willReturn(Optional.of(order));

        // when
        List<OrderItem> result = orderService.closeByExternalCancel(1L, null);

        // then
        assertThat(result).isEmpty();
        assertThat(order.getCancelReasonCode()).isEqualTo(CancelReasonCode.MEMBER_CANCELED);
        verify(orderStatusHistoryRepository, never()).save(any());
        verify(orderItemRepository, never()).findAllByOrderId(any());
    }

    @Test
    @DisplayName("취소 요청 중인 주문에 외부 취소가 오면 허용되지 않는 상태 변경 오류가 나고 이력을 남기지 않는다")
    void closeByExternalCancel_cancelRequested() {
        // given
        Order order = memberCancelRequestedOrder();
        given(orderRepository.findByIdForUpdate(1L)).willReturn(Optional.of(order));

        // when, then
        assertThatThrownBy(() -> orderService.closeByExternalCancel(1L, null))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(OrderErrorCode.INVALID_ORDER_STATUS_TRANSITION);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCEL_REQUESTED);
        verify(orderStatusHistoryRepository, never()).save(any());
    }

    private OrderMemberId orderMemberId(Long orderId, Long memberId) {
        return new OrderMemberId() {
            @Override
            public Long getOrderId() {
                return orderId;
            }

            @Override
            public Long getMemberId() {
                return memberId;
            }
        };
    }
}
