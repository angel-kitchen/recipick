package com.recipick.infra.portone;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipick.infra.portone.dto.PortOneWebhook;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class PortOneWebhookParser {

    private final ObjectMapper mapper = PortOneClientConfig.newPortOneObjectMapper();

    // 형식이 깨졌으면 빈 값. 서명이 맞는 요청이라 위조가 아니라 PortOne 쪽 변경일 수 있으므로 호출자가 WARN으로 남긴다.
    public Optional<PortOneWebhook> parse(String body) {
        try {
            return Optional.ofNullable(mapper.readValue(body, PortOneWebhook.class));
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }
}
