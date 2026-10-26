package com.recipick.domain.payment.controller;

import com.recipick.domain.payment.dto.request.PaymentAdminSearchRequest;
import com.recipick.domain.payment.dto.response.PaymentAdminDetailResponse;
import com.recipick.domain.payment.dto.response.PaymentAdminSummaryResponse;
import com.recipick.domain.payment.facade.PaymentAdminQueryFacade;
import com.recipick.global.response.ApiResponse;
import com.recipick.global.response.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// /api/admin/**은 SecurityConfig에서 MART_ADMIN에게도 열려 있다. 결제 조회는 SUPER_ADMIN 전용이라 클래스 단위로 막는다
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/payments")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class PaymentAdminController {

    private final PaymentAdminQueryFacade paymentAdminQueryFacade;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<PaymentAdminSummaryResponse>>> findPayments(
        @Valid @ModelAttribute PaymentAdminSearchRequest request
        ) {
        PageResponse<PaymentAdminSummaryResponse> response = paymentAdminQueryFacade.findPayments(request);
        return ResponseEntity.ok(ApiResponse.success("결제 내역을 조회했습니다.", response));
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<ApiResponse<PaymentAdminDetailResponse>> getPayment(
        @PathVariable Long paymentId
        ) {
        PaymentAdminDetailResponse response = paymentAdminQueryFacade.getPayment(paymentId);
        return ResponseEntity.ok(ApiResponse.success("결제 상세를 조회했습니다.", response));
    }
}
