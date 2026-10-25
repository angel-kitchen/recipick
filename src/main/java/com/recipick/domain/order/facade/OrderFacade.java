package com.recipick.domain.order.facade;

import com.recipick.domain.member.service.MemberService;
import com.recipick.domain.member.service.RegionService;
import com.recipick.domain.order.dto.OrderCreateResult;
import com.recipick.domain.order.dto.request.OrderCreateRequest;
import com.recipick.domain.order.dto.response.OrderCreateResponse;
import com.recipick.domain.order.entity.Order;
import com.recipick.domain.order.entity.OrderItem;
import com.recipick.domain.order.entity.enums.CancelReasonCode;
import com.recipick.domain.order.exception.OrderErrorCode;
import com.recipick.domain.order.service.CartService;
import com.recipick.domain.order.service.OrderService;
import com.recipick.domain.payment.dto.PaymentCreated;
import com.recipick.domain.payment.service.PaymentCommandService;
import com.recipick.domain.payment.service.PaymentService;
import com.recipick.domain.product.dto.MartResponse;
import com.recipick.domain.product.dto.ProductSummary;
import com.recipick.domain.product.dto.StockQuantity;
import com.recipick.domain.product.entity.MartStatus;
import com.recipick.domain.product.service.MartService;
import com.recipick.domain.product.service.ProductService;
import com.recipick.domain.product.service.ProductStockService;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.exception.CommonErrorCode;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class OrderFacade {

    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 64;
    private static final Pattern LEGAL_DONG_CODE = Pattern.compile("\\d{10}");

    private final OrderService orderService;
    private final CartService cartService;
    private final MemberService memberService;
    private final RegionService regionService;
    private final MartService martService;
    private final ProductService productService;
    private final ProductStockService productStockService;
    private final PaymentCommandService paymentCommandService;
    private final PaymentService paymentService;

    @Transactional
    public OrderCreateResult createOrder(Long memberId, String idempotencyKey, OrderCreateRequest request) {
        String normalizedKey = normalizeIdempotencyKey(idempotencyKey);
        if (new HashSet<>(request.cartItemIds()).size() != request.cartItemIds().size()) {
            throw new BusinessException(OrderErrorCode.DUPLICATE_ORDER_ITEM);
        }
        String requestHash = request.requestHash();

        // 탈퇴와 겹쳐도 한쪽만 성공하도록 첫 DB 작업으로 회원 행을 잠근다(락 순서는 MEMBER가 맨 앞).
        // 스냅샷이 잠금 뒤에 만들어져 아래 중복·결제 대기 확인이 앞선 요청의 커밋 결과를 본다
        memberService.getMemberForUpdate(memberId);
        // 재시도가 결제 대기 제한에 걸리지 않도록 같은 요청인지 먼저 확인한다
        Optional<Order> existing = orderService.findByIdempotencyKey(memberId, normalizedKey);
        if (existing.isPresent()) {
            return new OrderCreateResult(toExistingResponse(existing.get(), requestHash), false);
        }
        if (orderService.existsPendingOrder(memberId)) {
            throw new BusinessException(OrderErrorCode.PENDING_ORDER_EXISTS);
        }

        Long martId = memberService.getMartId(memberId)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.MART_NOT_SELECTED));
        MartResponse mart = martService.findMart(martId)
                .filter(found -> found.status() == MartStatus.OPEN)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.MART_NOT_OPEN));
        Long regionId = getDeliveryRegionId(request.legalDongCode(), mart.regionId());
        List<StockQuantity> quantities = cartService.findCartItems(memberId, request.cartItemIds()).stream()
                .map(cartItem -> new StockQuantity(cartItem.getProductId(), cartItem.getQuantity()))
                .toList();
        validateSameMart(quantities, martId);

        Order order = orderService.createPendingOrder(
                memberId, martId, regionId, normalizedKey, requestHash, request);
        // 상품 판매 상태·재고 검증과 차감을 잠근 상태에서 한 번에 하고, 그때의 가격으로 금액을 계산한다
        List<ProductSummary> products = productStockService.decreaseStocks(martId, quantities);
        List<OrderItem> items = orderService.completePendingOrder(order, products, quantities);
        PaymentCreated payment = paymentCommandService.create(order.getId(), order.getPayAmount());
        return new OrderCreateResult(OrderCreateResponse.of(order, items, payment), true);
    }

    // 락 순서 PAYMENT → ORDERS → MEMBER_POINT → POINT_EARNING → 재고에서 포인트 복구가 사이에 들어가야 해서
    // 주문 종료와 재고 복구를 나눈다. 결제 Facade가 한 트랜잭션 안에서 종료 → 포인트 복구 → 재고 복구 순으로 호출한다.
    // 따로 커밋되면 종료만 반영되고 재고 복구가 빠질 수 있어 바깥 트랜잭션 없이는 호출할 수 없게 한다.
    @Transactional(propagation = Propagation.MANDATORY)
    public List<StockQuantity> closeByPaymentFailure(
            Long orderId, CancelReasonCode reasonCode, String reasonDetail) {
        return toStockQuantities(orderService.closeByPaymentFailure(orderId, reasonCode, reasonDetail));
    }

    // 환불 완료도 포인트 복구가 주문과 재고 사이에 들어가야 해서 같은 이유로 재고 복구를 나눈다
    @Transactional(propagation = Propagation.MANDATORY)
    public List<StockQuantity> markRefunded(Long orderId) {
        return toStockQuantities(orderService.markRefunded(orderId));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void restoreStock(List<StockQuantity> items) {
        // 이미 종료된 주문은 복구할 재고가 없다
        if (items.isEmpty()) {
            return;
        }
        productStockService.increaseStocks(items);
    }

    private String normalizeIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BusinessException(CommonErrorCode.MISSING_IDEMPOTENCY_KEY);
        }
        if (idempotencyKey.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        // 대소문자만 다른 재시도도 같은 키로 찾아야 한다
        return idempotencyKey.toLowerCase(Locale.ROOT);
    }

    private OrderCreateResponse toExistingResponse(Order order, String requestHash) {
        if (!order.getRequestHash().equals(requestHash)) {
            throw new BusinessException(CommonErrorCode.IDEMPOTENCY_KEY_PAYLOAD_MISMATCH);
        }
        PaymentCreated payment = paymentService.getCreatedByOrderId(order.getId());
        return OrderCreateResponse.of(order, orderService.findOrderItems(order.getId()), payment);
    }

    // 배송 가능 여부는 배송지 법정동이 이용 마트의 법정동과 같은지로 판정한다
    private Long getDeliveryRegionId(String legalDongCode, Long martRegionId) {
        if (legalDongCode == null || !LEGAL_DONG_CODE.matcher(legalDongCode).matches()) {
            throw new BusinessException(OrderErrorCode.INVALID_DELIVERY_INFORMATION);
        }
        return regionService.getRegionIdByLegalDongCode(legalDongCode)
                .filter(martRegionId::equals)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.OUTSIDE_DELIVERY_AREA));
    }

    // 상품 엔티티를 미리 읽으면 재고 차감의 잠금 조회가 그 엔티티의 이전 재고 값으로 계산하므로 마트 ID만 조회한다
    private void validateSameMart(List<StockQuantity> quantities, Long martId) {
        Map<Long, Long> martIds = productService.findMartIdsOf(
                quantities.stream().map(StockQuantity::productId).toList());
        if (martIds.values().stream().anyMatch(productMartId -> !productMartId.equals(martId))) {
            throw new BusinessException(OrderErrorCode.CART_HAS_PREVIOUS_MART_ITEMS);
        }
    }

    private List<StockQuantity> toStockQuantities(List<OrderItem> items) {
        return items.stream()
                .map(item -> new StockQuantity(item.getProductId(), item.getQuantity()))
                .toList();
    }
}
