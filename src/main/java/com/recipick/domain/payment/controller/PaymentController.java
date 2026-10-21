package com.recipick.domain.payment.controller;

import com.recipick.domain.payment.dto.request.PaymentConfirmRequest;
import com.recipick.domain.payment.dto.response.PaymentConfirmResponse;
import com.recipick.domain.payment.facade.PaymentConfirmFacade;
import com.recipick.global.response.ApiResponse;
import com.recipick.global.security.LoginMember;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentConfirmFacade paymentConfirmFacade;

    @PostMapping("/api/payments/confirm")
    public ResponseEntity<ApiResponse<PaymentConfirmResponse>> confirm(
        @LoginMember Long memberId,
        @Valid @RequestBody PaymentConfirmRequest request
    ) {
        PaymentConfirmResponse response = paymentConfirmFacade.confirm(
            memberId, request.orderId(), request.portonePaymentId());
        return ResponseEntity.ok(ApiResponse.success("결제가 확정되었습니다.", response));
    }
}
