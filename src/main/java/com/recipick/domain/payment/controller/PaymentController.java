package com.recipick.domain.payment.controller;

import com.recipick.domain.payment.dto.PaymentCancelResult;
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

    // 결제 전·후 취소가 같은 엔드포인트다. 결과에 따라 200(끝남)·202(PortOne 결과 대기)로 갈린다
    @PostMapping("/api/payments/{paymentId}/cancel")
    public ResponseEntity<ApiResponse<PaymentCancelResponse>> cancel(
        @LoginMember Long memberId,
        @PathVariable Long paymentId,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @Valid @RequestBody PaymentCancelRequest request
    ) {
        PaymentCancelResult result = paymentCancelFacade.cancel(memberId, paymentId, idempotencyKey, request.reason());
        return switch (result.outcome()) {
            case CANCELED_BEFORE_PAY ->
                ResponseEntity.ok(ApiResponse.success("결제 대기 주문을 취소했습니다.", result.response()));
            case CANCELED ->
                ResponseEntity.ok(ApiResponse.success("결제가 취소되었습니다.", result.response()));
            case PENDING ->
                ResponseEntity.accepted().body(ApiResponse.success("취소 결과를 확인하고 있습니다.", result.response()));
            // 실패는 Facade가 예외로 바꿔 GlobalExceptionHandler가 409·500으로 답한다. 여기 오면 버그다
            case PG_FAILED, REQUEST_REJECTED ->
                throw new IllegalStateException("실패 outcome이 응답으로 넘어왔습니다: " + result.outcome());
        };
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
