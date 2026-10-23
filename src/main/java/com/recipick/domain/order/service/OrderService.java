package com.recipick.domain.order.service;

import com.recipick.domain.order.dto.request.OrderCreateRequest;
import com.recipick.domain.order.dto.request.OrderSearchRequest;
import com.recipick.domain.order.dto.response.OrderDetailResponse;
import com.recipick.domain.order.dto.response.OrderSummaryResponse;
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
import com.recipick.domain.product.dto.ProductSummary;
import com.recipick.domain.product.dto.StockQuantity;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.exception.CommonErrorCode;
import com.recipick.global.exception.ErrorCode;
import com.recipick.global.response.PageResponse;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

    // 결제 전 회원 취소도 결제 실패와 같은 종료 흐름을 탄다. 마트 거절은 결제 후 환불 흐름을 타므로 이 메서드로 종료하면 안 된다
    private static final Set<CancelReasonCode> BEFORE_PAYMENT_CLOSE_REASONS = EnumSet.of(
            CancelReasonCode.PAYMENT_FAILED, CancelReasonCode.PAYMENT_TIMEOUT, CancelReasonCode.AMOUNT_MISMATCH,
            CancelReasonCode.MEMBER_CANCELED);

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final OrderNumberGenerator orderNumberGenerator;

    public Optional<Order> findByIdempotencyKey(Long memberId, String idempotencyKey) {
        return orderRepository.findByMemberIdAndIdempotencyKey(memberId, idempotencyKey);
    }

    public boolean existsPendingOrder(Long memberId) {
        return orderRepository.existsByPendingMemberId(memberId);
    }

    // pending_member_id가 UNIQUE라 회원당 많아야 한 건이다
    public Optional<Order> findPendingOrder(Long memberId) {
        return orderRepository.findByPendingMemberId(memberId);
    }

    // 주문명이 첫 상품 기준이라 담은 순서대로 돌려준다
    public List<OrderItem> findOrderItems(Long orderId) {
        return orderItemRepository.findAllByOrderIdOrderByIdAsc(orderId);
    }

    // 금액은 재고를 잠근 뒤 completePendingOrder에서 채운다. 락 순서 ORDERS → 재고를 지키려고 주문부터 저장한다
    @Transactional
    public Order createPendingOrder(Long memberId, Long martId, Long regionId, String idempotencyKey,
            String requestHash, OrderCreateRequest request) {
        Order order = Order.builder()
                .orderNumber(generateOrderNumber())
                .memberId(memberId)
                .martId(martId)
                .pendingMemberId(memberId)
                .idempotencyKey(idempotencyKey)
                .requestHash(requestHash)
                .status(OrderStatus.PENDING_PAYMENT)
                .martStatus(MartStatus.NONE)
                .usedPoint(0L)
                .receiverName(request.receiverName())
                .receiverPhone(request.receiverPhone())
                .address(request.address())
                .detailAddress(request.detailAddress())
                .regionId(regionId)
                .build();
        // 앞선 중복 확인은 잠금 없이 읽어 동시 요청이 함께 통과할 수 있으므로 UNIQUE 제약으로 최종 판정한다.
        // 재고를 잠그기 전에 flush해 중복 요청이 상품 락을 기다리지 않고 바로 끝나게 한다
        try {
            orderRepository.saveAndFlush(order);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(toCreateConflict(e), e);
        }
        orderStatusHistoryRepository.save(history(order, OrderStatusType.ORDER, null,
                order.getStatus().name(), OrderStatusActor.MEMBER, null));
        return order;
    }

    // products는 quantities와 같은 순서로 차감된 상품이다
    @Transactional
    public List<OrderItem> completePendingOrder(
            Order order, List<ProductSummary> products, List<StockQuantity> quantities) {
        List<OrderItem> items = new ArrayList<>(products.size());
        for (int i = 0; i < products.size(); i++) {
            ProductSummary product = products.get(i);
            int quantity = quantities.get(i).quantity();
            items.add(OrderItem.builder()
                    .order(order)
                    .productId(product.productId())
                    .productName(product.name())
                    .unitPrice(product.price())
                    .saleUnitAmount(product.saleUnitAmount())
                    .saleUnit(product.saleUnit())
                    .lineAmount(product.price() * quantity)
                    .quantity(quantity)
                    .build());
        }
        long productAmount = items.stream().mapToLong(OrderItem::getLineAmount).sum();
        OrderAmount amount = OrderAmount.of(productAmount);
        order.confirmAmounts(amount.productAmount(), amount.deliveryFee(), amount.payAmount());
        return orderItemRepository.saveAll(items);
    }

    public Long getMemberIdOf(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND))
                .getMemberId();
    }

    public PageResponse<OrderSummaryResponse> searchMemberOrders(
            Long memberId, OrderSearchRequest request, Pageable pageable) {
        Page<Order> orders = orderRepository.searchByMember(memberId, request.statusGroup(), pageable);
        // 주문마다 상품을 읽으면 페이지 크기만큼 쿼리가 늘어나므로 한 번에 읽어 주문별로 나눈다
        Map<Long, List<OrderItem>> itemsByOrderId = orderItemRepository.findAllByOrderIdInOrderByIdAsc(
                        orders.getContent().stream().map(Order::getId).toList()).stream()
                .collect(Collectors.groupingBy(item -> item.getOrder().getId()));
        return PageResponse.from(orders.map(order ->
                OrderSummaryResponse.of(order, itemsByOrderId.get(order.getId()))));
    }

    // 다른 회원의 주문이면 "그 주문은 있다"를 알리지 않도록 없는 주문과 같이 답한다
    public OrderDetailResponse getMemberOrder(Long memberId, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .filter(found -> found.getMemberId().equals(memberId))
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
        return OrderDetailResponse.of(order, findOrderItems(orderId));
    }

    // 실패·취소된 결제도 결제 내역에 보여야 하므로 주문 상태로 거르지 않는다
    public List<Long> findOrderIdsOf(Long memberId) {
        return orderRepository.findIdsByMemberId(memberId);
    }

    // 없는 주문 ID는 결과에서 빠진다
    public Map<Long, Long> findMemberIdsOf(List<Long> orderIds) {
        if (orderIds.isEmpty()) {
            return Map.of();
        }
        return orderRepository.findMemberIdsByIdIn(orderIds).stream()
                .collect(Collectors.toMap(OrderMemberId::getOrderId, OrderMemberId::getMemberId));
    }

    // 결제 Facade가 PAYMENT를 먼저 잠근 트랜잭션 안에서 호출한다(락 순서 PAYMENT → ORDERS)
    @Transactional
    public void markPaid(Long orderId, LocalDateTime paidAt) {
        Order order = getOrderForUpdate(orderId);
        // 결제 확정 API·웹훅·만료 큐가 같은 성공 결과를 반복 전달할 수 있다
        if (order.getStatus() == OrderStatus.PAID) {
            return;
        }

        OrderStatus fromStatus = order.getStatus();
        MartStatus fromMartStatus = order.getMartStatus();
        order.markPaid(paidAt);

        orderStatusHistoryRepository.saveAll(List.of(
                systemHistory(order, OrderStatusType.ORDER, fromStatus.name(), order.getStatus().name(), null),
                systemHistory(order, OrderStatusType.MART, fromMartStatus.name(), order.getMartStatus().name(), null)
        ));
    }

    // 재고 복구는 호출한 Facade가 하므로, 이번 호출로 종료된 경우에만 주문 상품을 돌려준다
    @Transactional
    public List<OrderItem> closeByPaymentFailure(
            Long orderId, CancelReasonCode reasonCode, String reasonDetail) {
        if (!BEFORE_PAYMENT_CLOSE_REASONS.contains(reasonCode)) {
            throw new IllegalArgumentException("결제 전 종료 사유가 아닙니다: " + reasonCode);
        }
        Order order = getOrderForUpdate(orderId);
        // 실패·만료 결과가 반복 전달돼도 최초 취소 사유를 덮어쓰거나 재고를 다시 복구하지 않는다
        if (order.getStatus() == OrderStatus.CANCELED) {
            return List.of();
        }

        OrderStatus fromStatus = order.getStatus();
        order.cancel(reasonCode, reasonDetail);

        orderStatusHistoryRepository.save(systemHistory(
                order, OrderStatusType.ORDER, fromStatus.name(), order.getStatus().name(), reasonCode.name()));
        return orderItemRepository.findAllByOrderId(orderId);
    }

    // 중복 요청은 결제 쪽이 PAYMENT 락과 Idempotency-Key로 거른다.
    // 주문은 같은 요청인지 알 수 없으므로 PAID가 아니면 이미 취소 요청 중이어도 상태를 바꾸지 않고 거절만 한다
    @Transactional
    public MemberCancelRequestResult markMemberCancelRequested(Long orderId, String reasonDetail) {
        Order order = getOrderForUpdate(orderId);
        if (order.getStatus() != OrderStatus.PAID) {
            return MemberCancelRequestResult.INVALID_ORDER_STATUS;
        }
        if (!order.isCancelableByMember()) {
            return MemberCancelRequestResult.MART_ALREADY_PREPARING;
        }

        OrderStatus fromStatus = order.getStatus();
        order.requestMemberCancel(reasonDetail);

        orderStatusHistoryRepository.save(history(order, OrderStatusType.ORDER, fromStatus.name(),
                order.getStatus().name(), OrderStatusActor.MEMBER, CancelReasonCode.MEMBER_CANCELED.name()));
        return MemberCancelRequestResult.REQUESTED;
    }

    @Transactional
    public void restorePaidAfterMemberCancelFailure(Long orderId) {
        Order order = getOrderForUpdate(orderId);
        // PG 취소 실패 결과가 반복 전달될 수 있다
        if (order.getStatus() == OrderStatus.PAID) {
            return;
        }

        OrderStatus fromStatus = order.getStatus();
        order.restorePaidAfterMemberCancelFailure();

        orderStatusHistoryRepository.save(systemHistory(
                order, OrderStatusType.ORDER, fromStatus.name(), order.getStatus().name(), null));
    }

    // 재고 복구는 호출한 Facade가 포인트 복구 뒤에 하므로, 이번 호출로 환불 완료된 경우에만 주문 상품을 돌려준다
    @Transactional
    public List<OrderItem> markRefunded(Long orderId) {
        Order order = getOrderForUpdate(orderId);
        // 웹훅·재조회가 같은 환불 성공을 반복 전달해도 재고를 다시 복구하지 않는다
        if (order.getStatus() == OrderStatus.REFUNDED) {
            return List.of();
        }

        OrderStatus fromStatus = order.getStatus();
        order.markRefunded();

        orderStatusHistoryRepository.save(systemHistory(order, OrderStatusType.ORDER, fromStatus.name(),
                order.getStatus().name(), order.getCancelReasonCode().name()));
        return orderItemRepository.findAllByOrderId(orderId);
    }

    // 난수 10자리라 겹칠 일은 거의 없지만, 겹치면 flush에서 결제 대기 충돌로 잘못 응답하므로 미리 피한다
    private String generateOrderNumber() {
        String orderNumber = orderNumberGenerator.generate();
        while (orderRepository.existsByOrderNumber(orderNumber)) {
            orderNumber = orderNumberGenerator.generate();
        }
        return orderNumber;
    }

    // 멱등 키 충돌은 같은 요청이 먼저 처리 중인 것이고, 그 밖의 충돌은 pending_member_id로 결제 대기 주문이 이미 생긴 것이다
    private ErrorCode toCreateConflict(DataIntegrityViolationException e) {
        String message = e.getMostSpecificCause().getMessage();
        if (message != null && message.contains(Order.IDEMPOTENCY_KEY_CONSTRAINT)) {
            return CommonErrorCode.DUPLICATE_REQUEST_IN_PROGRESS;
        }
        return OrderErrorCode.PENDING_ORDER_EXISTS;
    }

    private Order getOrderForUpdate(Long orderId) {
        return orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
    }

    private OrderStatusHistory systemHistory(
            Order order, OrderStatusType statusType, String from, String to, String reason) {
        return history(order, statusType, from, to, OrderStatusActor.SYSTEM, reason);
    }

    private OrderStatusHistory history(
            Order order, OrderStatusType statusType, String from, String to, OrderStatusActor actor, String reason) {
        return OrderStatusHistory.builder()
                .order(order)
                .statusType(statusType)
                .fromStatus(from)
                .toStatus(to)
                .actor(actor)
                .reason(reason)
                .build();
    }
}
