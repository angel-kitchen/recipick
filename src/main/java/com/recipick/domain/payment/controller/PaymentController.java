package com.recipick.domain.payment.controller;

import com.recipick.domain.payment.dto.request.PaymentCancelRequest;
import com.recipick.domain.payment.dto.request.PaymentConfirmRequest;
import com.recipick.domain.payment.dto.response.PaymentCancelResponse;
import com.recipick.domain.payment.dto.response.PaymentConfirmResponse;
import com.recipick.domain.payment.dto.response.PaymentDetailResponse;
import com.recipick.domain.payment.dto.response.PaymentHistoryResponse;
import com.recipick.domain.payment.facade.PaymentCancelFacade;
import com.recipick.domain.payment.facade.PaymentConfirmFacade;
import com.recipick.domain.payment.facade.PaymentQueryFacade;
import com.recipick.global.response.ApiResponse;
import com.recipick.global.response.PageResponse;
import com.recipick.global.security.LoginMember;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentConfirmFacade paymentConfirmFacade;
    private final PaymentCancelFacade paymentCancelFacade;
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

    // 결제 전·후 취소가 같은 엔드포인트다. 지금은 결제 전(IN_PROGRESS)만 처리하고 PAID는 Processor가 409로 막는다
    @PostMapping("/api/payments/{paymentId}/cancel")
    public ResponseEntity<ApiResponse<PaymentCancelResponse>> cancel(
        @LoginMember Long memberId,
        @PathVariable Long paymentId,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @Valid @RequestBody PaymentCancelRequest request
    ) {
        PaymentCancelResponse response = paymentCancelFacade.cancel(
            memberId, paymentId, idempotencyKey, request.reason());
        return ResponseEntity.ok(ApiResponse.success("결제 대기 주문을 취소했습니다.", response));
    }

    @GetMapping("/api/payments/orders/{orderId}")
    public ResponseEntity<ApiResponse<PaymentDetailResponse>> getByOrderId(
        @LoginMember Long memberId,
        @PathVariable Long orderId
    ) {
        PaymentDetailResponse response = paymentQueryFacade.getByOrderId(memberId, orderId);
        return ResponseEntity.ok(ApiResponse.success("결제 정보를 조회했습니다.", response));
    }

    // size는 application.yml의 spring.data.web.pageable.max-page-size(100)로 잘린다
    @GetMapping("/api/payments")
    public ResponseEntity<ApiResponse<PageResponse<PaymentHistoryResponse>>> findMyHistory(
        @LoginMember Long memberId,
        Pageable pageable
    ) {
        PageResponse<PaymentHistoryResponse> response = paymentQueryFacade.findMyHistory(memberId, pageable);
        return ResponseEntity.ok(ApiResponse.success("결제 내역을 조회했습니다.", response));
    }
}
