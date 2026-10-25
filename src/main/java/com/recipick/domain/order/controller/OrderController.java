package com.recipick.domain.order.controller;

import com.recipick.domain.order.dto.OrderCreateResult;
import com.recipick.domain.order.dto.request.OrderCreateRequest;
import com.recipick.domain.order.dto.request.OrderPreviewRequest;
import com.recipick.domain.order.dto.request.OrderSearchRequest;
import com.recipick.domain.order.dto.response.OrderCreateResponse;
import com.recipick.domain.order.dto.response.OrderDetailResponse;
import com.recipick.domain.order.dto.response.OrderPreviewResponse;
import com.recipick.domain.order.dto.response.OrderSummaryResponse;
import com.recipick.domain.order.facade.OrderFacade;
import com.recipick.domain.order.facade.OrderQueryFacade;
import com.recipick.domain.order.service.OrderService;
import com.recipick.global.response.ApiResponse;
import com.recipick.global.response.PageResponse;
import com.recipick.global.security.LoginMember;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class OrderController {

    private final OrderFacade orderFacade;
    private final OrderQueryFacade orderQueryFacade;
    private final OrderService orderService;

    // size는 application.yml의 spring.data.web.pageable.max-page-size(100)로 잘린다. 정렬은 id 내림차순 고정이라 sort는 무시한다
    @GetMapping("/api/orders")
    public ResponseEntity<ApiResponse<PageResponse<OrderSummaryResponse>>> searchOrders(
            @LoginMember Long memberId,
            @ModelAttribute OrderSearchRequest request,
            Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success(orderService.searchMemberOrders(memberId, request, pageable)));
    }

    @GetMapping("/api/orders/{orderId}")
    public ResponseEntity<ApiResponse<OrderDetailResponse>> getOrder(
            @LoginMember Long memberId,
            @PathVariable Long orderId
    ) {
        return ResponseEntity.ok(ApiResponse.success(orderService.getMemberOrder(memberId, orderId)));
    }

    @PostMapping("/api/orders/preview")
    public ResponseEntity<ApiResponse<OrderPreviewResponse>> previewOrder(
            @LoginMember Long memberId,
            @Valid @RequestBody OrderPreviewRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(orderQueryFacade.previewOrder(memberId, request)));
    }

    @PostMapping("/api/orders")
    public ResponseEntity<ApiResponse<OrderCreateResponse>> createOrder(
            @LoginMember Long memberId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody OrderCreateRequest request
    ) {
        OrderCreateResult result = orderFacade.createOrder(memberId, idempotencyKey, request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(ApiResponse.success(result.response()));
    }
}
