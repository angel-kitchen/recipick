package com.recipick.domain.order.controller;

import com.recipick.domain.member.entity.enums.AdminRole;
import com.recipick.domain.order.dto.request.OrderAdminSearchRequest;
import com.recipick.domain.order.dto.response.OrderAdminDetailResponse;
import com.recipick.domain.order.dto.response.OrderAdminSummaryResponse;
import com.recipick.domain.order.dto.response.OrderMartStatusResponse;
import com.recipick.domain.order.service.OrderService;
import com.recipick.domain.order.facade.OrderQueryFacade;
import com.recipick.global.response.ApiResponse;
import com.recipick.global.response.PageResponse;
import com.recipick.global.security.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/orders")
public class OrderAdminController {

    private final OrderService orderService;
    private final OrderQueryFacade orderQueryFacade;

    // size는 application.yml의 spring.data.web.pageable.max-page-size(100)로 잘린다. 정렬은 id 내림차순 고정이라 sort는 무시한다
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<OrderAdminSummaryResponse>>> searchOrders(
            @AuthenticationPrincipal AuthPrincipal principal,
            @ModelAttribute OrderAdminSearchRequest request,
            Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success(orderQueryFacade.searchAdminOrders(
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
}
