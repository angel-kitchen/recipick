package com.recipick.domain.payment.controller;

import com.recipick.domain.payment.dto.request.PaymentConfirmRequest;
import com.recipick.domain.payment.dto.response.PaymentConfirmResponse;
import com.recipick.domain.payment.dto.response.PaymentDetailResponse;
import com.recipick.domain.payment.entity.Payment;
import com.recipick.domain.payment.facade.PaymentConfirmFacade;
import com.recipick.domain.payment.facade.PaymentQueryFacade;
import com.recipick.global.response.ApiResponse;
import com.recipick.global.security.LoginMember;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentConfirmFacade paymentConfirmFacade;
    private final PaymentQueryFacade paymentQueryFacade;

    @PostMapping("/api/payments/confirm")
    public ResponseEntity<ApiResponse<PaymentConfirmResponse>> confirm(
        @LoginMember Long memberId,
        @Valid @RequestBody PaymentConfirmRequest request
    ) {
        PaymentConfirmResponse response = paymentConfirmFacade.confirm(
            memberId, request.orderId(), request.portonePaymentId());
        return ResponseEntity.ok(ApiResponse.success("결제가 확정되었습니다.", response));
    }

    @GetMapping("/api/payments/orders/{orderId}")
    public ResponseEntity<ApiResponse<PaymentDetailResponse>> getByOrderId(
        @LoginMember Long memberId,
        @PathVariable Long orderId
    ) {
        PaymentDetailResponse response = paymentQueryFacade.getByOrderId(memberId, orderId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
