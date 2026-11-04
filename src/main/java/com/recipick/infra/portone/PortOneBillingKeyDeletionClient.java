package com.recipick.infra.portone;

import com.fasterxml.jackson.databind.JsonNode;
import com.recipick.infra.portone.dto.PortOneBillingKeyDeletionCode;
import com.recipick.infra.portone.dto.PortOneBillingKeyDeletionObservation;
import com.recipick.infra.portone.dto.PortOneBillingKeyDeletionObservation.Kind;
import java.net.URI;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

// 원격 호출 전용. URL·키 원문·예외 메시지·응답 원문을 반환하거나 로그에 남기지 않는다.
@Component
public class PortOneBillingKeyDeletionClient {
    private final RestClient client;
    private final String baseUrl;

    @Autowired
    public PortOneBillingKeyDeletionClient(@Qualifier("billingKeyDeletionRestClient") RestClient client,
                                           PortOneProperties properties) {
        this(client, properties.baseUrl());
    }

    public PortOneBillingKeyDeletionClient(RestClient client, String baseUrl) {
        this.client = client;
        this.baseUrl = baseUrl;
    }

    public PortOneBillingKeyDeletionObservation lookup(String key, String storeId) {
        try {
            return client.get().uri(uri(key, storeId, null, null)).exchange((request, response) -> {
                int http = response.getStatusCode().value();
                JsonNode body = read(response);
                if (http < 200 || http >= 300) {
                    return PortOneBillingKeyDeletionObservation.error(http, text(body, "type"));
                }
                PortOneBillingKeyDeletionObservation observation = PortOneBillingKeyDeletionObservation.found(
                    key, storeId, text(body, "status"), text(body, "billingKey"), text(body, "storeId"));
                // 실제 청구용 키 삭제를 테스트 작업에 섞지 않는다. 이미 삭제된 사실의 확인은 채널과 무관하다.
                if (observation.kind() == Kind.ISSUED && !allTestChannels(body)) {
                    return new PortOneBillingKeyDeletionObservation(Kind.REVIEW_REQUIRED,
                        PortOneBillingKeyDeletionCode.CHANNEL_NOT_TEST);
                }
                return observation;
            });
        } catch (ResourceAccessException e) {
            return PortOneBillingKeyDeletionObservation.unknown(PortOneErrorClassifier.isTimeout(e)
                ? PortOneBillingKeyDeletionCode.TIMEOUT : PortOneBillingKeyDeletionCode.HTTP_ERROR);
        } catch (RestClientException e) {
            return PortOneBillingKeyDeletionObservation.unknown(PortOneBillingKeyDeletionCode.INVALID_RESPONSE);
        }
    }

    // DELETE의 성공 응답은 힌트다. 호출자는 반드시 다시 GET하여 로컬 DELETED를 확정한다.
    public PortOneBillingKeyDeletionCode delete(String key, String storeId, String reason, String requester) {
        if (!"CUSTOMER".equals(requester) && !"ADMIN".equals(requester)) {
            throw new IllegalArgumentException("삭제 요청 주체가 올바르지 않습니다");
        }
        try {
            return client.delete().uri(uri(key, storeId, reason, requester)).exchange((request, response) -> {
                int http = response.getStatusCode().value();
                JsonNode body = read(response);
                String type = text(body, "type");
                if ((http >= 200 && http < 300)
                    || (http == 409 && "BILLING_KEY_ALREADY_DELETED".equals(type))
                    || (http == 404 && "BILLING_KEY_NOT_FOUND".equals(type))) {
                    return PortOneBillingKeyDeletionCode.DELETE_ACCEPTED;
                }
                if (http == 401 || http == 403) {
                    return PortOneBillingKeyDeletionCode.AUTH_ERROR;
                }
                if (http == 400) {
                    return PortOneBillingKeyDeletionCode.REQUEST_REJECTED;
                }
                return PortOneBillingKeyDeletionCode.HTTP_ERROR;
            });
        } catch (ResourceAccessException e) {
            return PortOneErrorClassifier.isTimeout(e)
                ? PortOneBillingKeyDeletionCode.TIMEOUT : PortOneBillingKeyDeletionCode.HTTP_ERROR;
        } catch (RestClientException e) {
            return PortOneBillingKeyDeletionCode.INVALID_RESPONSE;
        }
    }

    private URI uri(String key, String storeId, String reason, String requester) {
        if (key == null || key.isBlank() || storeId == null || storeId.isBlank()) {
            throw new IllegalArgumentException("빌링키 삭제 대상이 올바르지 않습니다");
        }
        UriComponentsBuilder uri = UriComponentsBuilder.fromUriString(baseUrl)
            .pathSegment("billing-keys", "{key}").queryParam("storeId", "{store}");
        if (requester != null) {
            uri.queryParam("reason", "{reason}").queryParam("requester", "{requester}");
            return uri.encode().buildAndExpand(Map.of("key", key, "store", storeId,
                "reason", reason, "requester", requester)).toUri();
        }
        return uri.encode().buildAndExpand(Map.of("key", key, "store", storeId)).toUri();
    }

    private static JsonNode read(ConvertibleClientHttpResponse response) {
        try {
            return response.bodyTo(JsonNode.class);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String text(JsonNode root, String field) {
        if (root == null || !root.isObject()) {
            return null;
        }
        JsonNode value = root.get(field);
        return value != null && value.isTextual() ? value.textValue() : null;
    }

    private static boolean allTestChannels(JsonNode root) {
        JsonNode channels = root == null ? null : root.get("channels");
        if (channels == null || !channels.isArray() || channels.isEmpty()) {
            return false;
        }
        for (JsonNode channel : channels) {
            if (!"TEST".equals(text(channel, "type"))) {
                return false;
            }
        }
        return true;
    }
}
