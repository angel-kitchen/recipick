package com.recipick.domain.payment.controller;

import com.recipick.domain.payment.facade.PortOneWebhookFacade;
import com.recipick.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PortOneWebhookController {

    private final PortOneWebhookFacade portOneWebhookFacade;

    // 서명 검증에 원문이 필요해 String으로 받는다. DTO로 바인딩하면 공백·키 순서가 바뀌어 서명이 깨진다.
    // 헤더는 required=false로 받아 누락도 서명 검증 결과(401 AUTH-010) 하나로 답한다
    @PostMapping("/api/webhooks/portone")
    public ResponseEntity<ApiResponse<Void>> receive(
        @RequestHeader(value = "webhook-id", required = false) String webhookId,
        @RequestHeader(value = "webhook-timestamp", required = false) String webhookTimestamp,
        @RequestHeader(value = "webhook-signature", required = false) String webhookSignature,
        @RequestBody String body
    ) {
        portOneWebhookFacade.receive(webhookId, webhookTimestamp, webhookSignature, body);
        return ResponseEntity.ok(ApiResponse.<Void>success("웹훅을 접수했습니다.", null));
    }
}
