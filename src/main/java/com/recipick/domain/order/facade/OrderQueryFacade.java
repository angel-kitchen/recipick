package com.recipick.domain.order.facade;

import com.recipick.domain.member.entity.enums.AdminRole;
import com.recipick.domain.order.dto.request.OrderAdminSearchRequest;
import com.recipick.domain.order.dto.response.OrderAdminSummaryResponse;
import com.recipick.global.audit.AdminAuditAction;
import com.recipick.global.audit.AdminAuditCommand;
import com.recipick.global.audit.AdminAuditRecorder;
import com.recipick.global.response.PageResponse;
import com.recipick.domain.member.service.MemberService;
import com.recipick.domain.member.service.MembershipService;
import com.recipick.domain.member.service.RegionService;
import com.recipick.domain.order.dto.OrderNameFormatter;
import com.recipick.domain.order.dto.request.OrderPreviewRequest;
import com.recipick.domain.order.dto.response.CartItemResponse;
import com.recipick.domain.order.dto.response.CartItemStatus;
import com.recipick.domain.order.dto.response.OrderPreviewResponse;
import com.recipick.domain.order.dto.response.OrderPreviewResponse.Delivery;
import com.recipick.domain.order.dto.response.OrderPreviewResponse.DeliveryStatus;
import com.recipick.domain.order.dto.response.OrderPreviewResponse.PendingOrder;
import com.recipick.domain.order.dto.response.OrderPreviewResponse.UnorderableReason;
import com.recipick.domain.order.entity.CartItem;
import com.recipick.domain.order.entity.Order;
import com.recipick.domain.order.exception.OrderErrorCode;
import com.recipick.domain.order.service.CartService;
import com.recipick.domain.order.service.OrderAmount;
import com.recipick.domain.order.service.OrderService;
import com.recipick.domain.payment.dto.PaymentCreated;
import com.recipick.domain.payment.service.PaymentService;
import com.recipick.domain.product.dto.MartResponse;
import com.recipick.domain.product.dto.ProductSummary;
import com.recipick.domain.product.entity.MartStatus;
import com.recipick.domain.product.service.MartService;
import com.recipick.domain.product.service.ProductService;
import com.recipick.global.exception.BusinessException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

// 미리보기는 각 Service의 읽기 전용 트랜잭션을 사용하고, 관리자 조회는 감사 기록과 함께 커밋한다.
@Component
@RequiredArgsConstructor
public class OrderQueryFacade {

    private static final Pattern LEGAL_DONG_CODE = Pattern.compile("\\d{10}");

    private final AdminAuditRecorder auditRecorder;
    private final MemberService memberService;
    private final MembershipService membershipService;
    private final RegionService regionService;
    private final MartService martService;
    private final CartService cartService;
    private final ProductService productService;
    private final OrderService orderService;
    private final PaymentService paymentService;
    private final Clock clock;

    @Transactional
    public PageResponse<OrderAdminSummaryResponse> searchAdminOrders(
            AdminRole role, Long adminMartId, OrderAdminSearchRequest request, Pageable pageable) {
        PageResponse<OrderAdminSummaryResponse> response =
                orderService.searchAdminOrders(role, adminMartId, request, pageable);
        Long scopedMartId = role == AdminRole.MART_ADMIN ? adminMartId : request.martId();
        Long auditMartId = scopedMartId == null ? null
                : martService.findMart(scopedMartId).map(MartResponse::martId).orElse(null);
        auditRecorder.record(new AdminAuditCommand(AdminAuditAction.ORDER_LIST_VIEW, null, auditMartId, Map.of()));
        return response;
    }

    public OrderPreviewResponse previewOrder(Long memberId, OrderPreviewRequest request) {
        if (new HashSet<>(request.cartItemIds()).size() != request.cartItemIds().size()) {
            throw new BusinessException(OrderErrorCode.DUPLICATE_ORDER_ITEM);
        }
        if (request.legalDongCode() == null || !LEGAL_DONG_CODE.matcher(request.legalDongCode()).matches()) {
            throw new BusinessException(OrderErrorCode.INVALID_DELIVERY_INFORMATION);
        }
        Set<UnorderableReason> reasons = EnumSet.noneOf(UnorderableReason.class);

        Long martId = memberService.getMartId(memberId).orElse(null);
        Optional<MartResponse> openMart = Optional.ofNullable(martId)
                .flatMap(martService::findMart)
                .filter(mart -> mart.status() == MartStatus.OPEN);
        if (martId == null) {
            reasons.add(UnorderableReason.MART_NOT_SELECTED);
        } else if (openMart.isEmpty()) {
            reasons.add(UnorderableReason.MART_NOT_OPEN);
        }

        Delivery delivery = judgeDelivery(request.legalDongCode(), openMart);
        if (delivery.status() != DeliveryStatus.AVAILABLE) {
            reasons.add(UnorderableReason.DELIVERY_UNAVAILABLE);
        }

        List<CartItemResponse> items = toItems(cartService.findCartItems(memberId, request.cartItemIds()), martId);
        if (items.stream().anyMatch(item -> item.status() == CartItemStatus.PREVIOUS_MART)) {
            reasons.add(UnorderableReason.HAS_PREVIOUS_MART_ITEMS);
        }
        List<CartItemResponse> purchasable = items.stream()
                .filter(item -> item.status() == CartItemStatus.AVAILABLE)
                .toList();
        if (purchasable.isEmpty()) {
            reasons.add(UnorderableReason.NO_PURCHASABLE_ITEMS);
        }

        PendingOrder pendingOrder = orderService.findPendingOrder(memberId).map(this::toPendingOrder).orElse(null);
        if (pendingOrder != null) {
            reasons.add(UnorderableReason.PENDING_ORDER_EXISTS);
        }

        // 살 수 있는 상품이 없으면 배송비도 붙지 않는다. 금액 계산은 주문 생성과 같은 OrderAmount를 쓴다
        OrderAmount amount = purchasable.isEmpty()
                ? new OrderAmount(0L, 0L, 0L)
                : OrderAmount.of(purchasable.stream().mapToLong(CartItemResponse::lineAmount).sum(),
                        membershipService.isActive(memberId));
        return new OrderPreviewResponse(reasons.isEmpty(), List.copyOf(reasons), delivery, items,
                amount.productAmount(), amount.deliveryFee(), amount.payAmount(), pendingOrder);
    }

    // 배송 가능 여부는 배송지 법정동이 이용 마트의 법정동과 같은지로 판정한다.
    // 다른 마트 지역 판정은 회원 서비스 지역 확인과 같이 그 지역의 첫 번째 운영 중인 마트로 한다
    private Delivery judgeDelivery(String legalDongCode, Optional<MartResponse> openMart) {
        Optional<Long> regionId = regionService.getRegionIdByLegalDongCode(legalDongCode);
        if (openMart.isPresent() && regionId.filter(openMart.get().regionId()::equals).isPresent()) {
            return new Delivery(DeliveryStatus.AVAILABLE, openMart.get().name());
        }
        return regionId.flatMap(id -> martService.findOpenMarts(id).stream().findFirst())
                .map(mart -> new Delivery(DeliveryStatus.OTHER_MART_AREA, mart.name()))
                .orElseGet(() -> new Delivery(DeliveryStatus.OUTSIDE_SERVICE_AREA, null));
    }

    // 상품은 소프트 삭제만 하므로 담긴 상품은 삭제 표시가 된 채로라도 항상 조회된다
    private List<CartItemResponse> toItems(List<CartItem> cartItems, Long martId) {
        Map<Long, ProductSummary> products = productService.findProducts(
                        cartItems.stream().map(CartItem::getProductId).toList()).stream()
                .collect(Collectors.toMap(ProductSummary::productId, Function.identity()));
        return cartItems.stream()
                .map(cartItem -> CartItemResponse.of(cartItem, products.get(cartItem.getProductId()), martId))
                .toList();
    }

    private PendingOrder toPendingOrder(Order order) {
        PaymentCreated payment = paymentService.getCreatedByOrderId(order.getId());
        return new PendingOrder(
                order.getId(),
                order.getOrderNumber(),
                payment.paymentId(),
                OrderNameFormatter.format(orderService.findOrderItems(order.getId())),
                payment.amount(),
                payment.expiresAt(),
                LocalDateTime.now(clock).isBefore(payment.expiresAt())
        );
    }
}
