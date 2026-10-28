package com.recipick.domain.order.controller;

import com.recipick.domain.member.entity.enums.AdminRole;
import com.recipick.domain.order.dto.request.OrderAdminSearchRequest;
import com.recipick.domain.order.dto.request.OrderRejectRequest;
import com.recipick.domain.order.dto.response.OrderAdminDetailResponse;
import com.recipick.domain.order.dto.response.OrderAdminSummaryResponse;
import com.recipick.domain.order.dto.response.OrderMartStatusResponse;
import com.recipick.domain.order.service.OrderService;
import com.recipick.domain.payment.dto.MartRejectionPaymentResult;
import com.recipick.domain.payment.dto.response.MartRejectionResponse;
import com.recipick.domain.payment.facade.MartRejectionFacade;
import com.recipick.global.response.ApiResponse;
import com.recipick.global.response.PageResponse;
import com.recipick.global.security.AuthPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/orders")
public class OrderAdminController {

    private final OrderService orderService;
    private final MartRejectionFacade martRejectionFacade;

    // size는 application.yml의 spring.data.web.pageable.max-page-size(100)로 잘린다. 정렬은 id 내림차순 고정이라 sort는 무시한다
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<OrderAdminSummaryResponse>>> searchOrders(
        @AuthenticationPrincipal AuthPrincipal principal,
        @ModelAttribute OrderAdminSearchRequest request,
        Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success(orderService.searchAdminOrders(
            AdminRole.valueOf(principal.role()), principal.martId(), request, pageable)));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResponse<OrderAdminDetailResponse>> getOrder(
        @AuthenticationPrincipal AuthPrincipal principal,
        @PathVariable Long orderId
    ) {
        return ResponseEntity.ok(ApiResponse.success(orderService.getAdminOrder(
            AdminRole.valueOf(principal.role()), principal.martId(), orderId)));
    }

    @PostMapping("/{orderId}/accept")
    public ResponseEntity<ApiResponse<OrderMartStatusResponse>> acceptOrder(
        @AuthenticationPrincipal AuthPrincipal principal,
        @PathVariable Long orderId
    ) {
        return ResponseEntity.ok(ApiResponse.success(orderService.acceptByMart(
            AdminRole.valueOf(principal.role()), principal.martId(), principal.id(), orderId)));
    }

    // 거절은 결제 취소까지 이어져 결제 Facade가 처리한다. 결과에 따라 200(취소 완료)·202(PortOne 결과 확인 중)로 갈린다
    @PostMapping("/{orderId}/reject")
    public ResponseEntity<ApiResponse<MartRejectionResponse>> rejectOrder(
        @AuthenticationPrincipal AuthPrincipal principal,
        @PathVariable Long orderId,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @Valid @RequestBody OrderRejectRequest request
    ) {
        MartRejectionPaymentResult result = martRejectionFacade.reject(AdminRole.valueOf(principal.role()),
            principal.martId(), principal.id(), orderId, idempotencyKey, request.reason());
        return switch (result.outcome()) {
            case COMPLETED ->
                ResponseEntity.ok(ApiResponse.success("주문을 거절하고 결제를 취소했습니다.", result.response()));
            case PENDING -> ResponseEntity.accepted()
                .body(ApiResponse.success("주문을 거절했습니다. 결제 취소 결과를 확인하고 있습니다.", result.response()));
            // 실패는 Facade가 예외로 바꿔 GlobalExceptionHandler가 409·500으로 답한다. 여기 오면 버그다
            case PG_FAILED, REQUEST_REJECTED ->
                throw new IllegalStateException("실패 outcome이 응답으로 넘어왔습니다: " + result.outcome());
        };
    }
}
