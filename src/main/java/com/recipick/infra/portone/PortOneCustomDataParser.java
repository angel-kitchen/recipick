package com.recipick.infra.portone;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * PortOne 결제 조회 응답의 {@code customData}(JSON 문자열)에서 주문 ID를 꺼낸다.
 *
 * <p>잠정 구현: 프론트가 {@code requestPayment}에 {@code customData: { orderId }}를 넣는다는 계약과
 * PortOne 테스트 채널에서 확인한 실제 응답 형식으로 최종 확정한다. 값이 없거나 해석할 수 없으면
 * 예외 대신 {@code null}을 돌려주고, 검증 실패 판단은 도메인(PaymentVerifier)이 한다.
 */
public final class PortOneCustomDataParser {

    private static final String ORDER_ID_FIELD = "orderId";
    private static final ObjectMapper MAPPER = PortOneClientConfig.newPortOneObjectMapper();

    private PortOneCustomDataParser() {
    }

    public static Long orderIdOf(String customData) {
        if (customData == null || customData.isBlank()) {
            return null;
        }
        JsonNode root;
        try {
            root = MAPPER.readTree(customData);
        } catch (JsonProcessingException e) {
            return null;
        }
        if (root == null || !root.isObject()) {
            return null;
        }
        JsonNode orderId = root.get(ORDER_ID_FIELD);
        // 문자열 "10"이나 소수 10.5를 Jackson 강제 변환으로 받아들이지 않도록 정수 노드만 인정한다
        if (orderId == null || !orderId.isIntegralNumber() || !orderId.canConvertToLong()) {
            return null;
        }
        return orderId.longValue();
    }
}
