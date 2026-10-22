package com.recipick.domain.order.controller;

import com.recipick.domain.order.dto.OrderCreateResult;
import com.recipick.domain.order.dto.request.OrderCreateRequest;
import com.recipick.domain.order.dto.response.OrderCreateResponse;
import com.recipick.domain.order.facade.OrderFacade;
import com.recipick.global.response.ApiResponse;
import com.recipick.global.security.LoginMember;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class OrderController {

    private final OrderFacade orderFacade;

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
