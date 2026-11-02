package com.recipick.infra.portone;

import com.recipick.infra.portone.dto.PortOneCancelOutcome;
import com.recipick.infra.portone.dto.PortOneErrorResponse;
import com.recipick.infra.portone.dto.PortOneMembershipCancelRequest;
import com.recipick.infra.portone.dto.PortOneMembershipCancelResponse;
import com.recipick.infra.portone.dto.PortOneMembershipCancelResult;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse;
import org.springframework.web.client.RestClientException;

// 멤버십 환불용 PortOne V2 금액 지정 취소. 외부 호출만 담당하고 어느 환불을 어떻게 바꿀지는 결제 Facade가 정한다.
// DB 트랜잭션 밖에서, 환불 행마다 고정된 멱등 키(공식 형식대로 헤더 값에 쌍따옴표 포함)와 같은 본문으로 부른다.
// 응답은 예외로 던지지 않고 분류해 돌려준다 — 호출 전에 REQUESTED가 커밋돼 있어 어떤 응답이 와도 뒤의 재조회·결과 반영이 정리한다.
// 기존 PortOnePaymentClient의 전액 취소와 별도 빈(읽기 한도 60초)이다. 로그에는 결제 ID·결과 분류만 남긴다
@Slf4j
@Component
public class PortOneMembershipRefundClient {

    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final RestClient restClient;

    public PortOneMembershipRefundClient(
        @Qualifier(PortOneClientConfig.MEMBERSHIP_REFUND_REST_CLIENT_BEAN) RestClient restClient) {
        this.restClient = restClient;
    }

    public PortOneMembershipCancelResult cancel(String portonePaymentId, String pgIdempotencyKey,
                                                PortOneMembershipCancelRequest body) {
        if (pgIdempotencyKey == null || pgIdempotencyKey.isBlank()) {
            throw new IllegalArgumentException("환불 행의 PG 멱등 키가 필요합니다");
        }
        try {
            PortOneMembershipCancelResult result = restClient.post()
                .uri("/payments/{paymentId}/cancel", portonePaymentId)
                .header(IDEMPOTENCY_KEY_HEADER, "\"" + pgIdempotencyKey + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                // 4xx·5xx도 예외가 아니라 분류 대상이라 retrieve() 대신 응답을 직접 받는다
                .exchange((request, response) -> {
                    int status = response.getStatusCode().value();
                    if (response.getStatusCode().is2xxSuccessful()) {
                        return PortOneMembershipCancelResult.from(status,
                            response.bodyTo(PortOneMembershipCancelResponse.class));
                    }
                    return PortOneMembershipCancelResult.fromError(status, errorTypeOf(response));
                });
            if (result.outcome() == PortOneCancelOutcome.REJECTED) {
                log.error("PortOne 멤버십 취소 요청 거절: paymentId={}, detail={}", portonePaymentId, result.detail());
            } else if (result.outcome() != PortOneCancelOutcome.SUCCEEDED) {
                log.warn("PortOne 멤버십 취소 미완료: paymentId={}, outcome={}, detail={}", portonePaymentId,
                    result.outcome(), result.detail());
            }
            return result;
        } catch (ResourceAccessException e) {
            String detail = isTimeout(e) ? "timeout" : "연결 실패";
            log.warn("PortOne 멤버십 취소 {}: paymentId={}", detail, portonePaymentId);
            return PortOneMembershipCancelResult.unconfirmed(detail);
        } catch (RestClientException e) {
            // 성공 응답 본문을 DTO로 읽지 못한 경우 등. 응답 원문이 들어갈 수 있어 예외 객체는 남기지 않는다
            log.warn("PortOne 멤버십 취소 응답 처리 실패: paymentId={}, cause={}", portonePaymentId,
                e.getClass().getSimpleName());
            return PortOneMembershipCancelResult.unconfirmed("응답 처리 실패");
        }
    }

    private static String errorTypeOf(ConvertibleClientHttpResponse response) {
        try {
            PortOneErrorResponse error = response.bodyTo(PortOneErrorResponse.class);
            return error == null ? null : error.type();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static boolean isTimeout(ResourceAccessException e) {
        Throwable cause = e.getCause();
        while (cause != null) {
            if (cause instanceof SocketTimeoutException
                || cause instanceof HttpTimeoutException
                || cause instanceof TimeoutException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
