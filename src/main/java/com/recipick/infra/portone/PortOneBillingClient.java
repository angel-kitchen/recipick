package com.recipick.infra.portone;

import com.recipick.global.exception.BusinessException;
import com.recipick.global.exception.CommonErrorCode;
import com.recipick.infra.portone.dto.PortOneBillingKeyInfo;
import com.recipick.infra.portone.dto.PortOneBillingKeyInfoResponse;
import com.recipick.infra.portone.dto.PortOneBillingKeyStatus;
import com.recipick.infra.portone.dto.PortOneBillingPaymentOutcome;
import com.recipick.infra.portone.dto.PortOneBillingPaymentRequest;
import com.recipick.infra.portone.dto.PortOneBillingPaymentResponse;
import com.recipick.infra.portone.dto.PortOneBillingPaymentResult;
import com.recipick.infra.portone.dto.PortOneErrorResponse;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

// PortOne V2 빌링키 조회와 빌링키 청구. 외부 호출만 담당하고 등록·결제 가능 여부 같은 업무 판단은 domain이 한다.
// DB 트랜잭션 밖에서 호출한다. 삭제(DELETE /billing-keys/{key})는 6단계에서 호출자와 함께 추가한다.
// 빌링키 원문은 조회의 요청 URL 경로와 청구의 요청 본문에 들어간다. 그래서 예외 객체(메시지에 URL·본문이 섞일 수 있다)를
// 로그에 남기지 않고 BusinessException의 cause로도 넘기지 않는다. 로그에는 결제 ID·HTTP 상태와 허용 목록의 분류 코드만 남긴다
@Slf4j
@Component
public class PortOneBillingClient {

    private final RestClient restClient;

    public PortOneBillingClient(@Qualifier(PortOneClientConfig.REST_CLIENT_BEAN) RestClient restClient) {
        this.restClient = restClient;
    }

    // 404(BILLING_KEY_NOT_FOUND)는 empty. 401·403은 API Secret·상점 설정 문제라 ERROR로 남긴다
    public Optional<PortOneBillingKeyInfo> getBillingKey(String billingKey) {
        try {
            PortOneBillingKeyInfoResponse response = restClient.get()
                .uri("/billing-keys/{billingKey}", billingKey)
                .retrieve()
                .body(PortOneBillingKeyInfoResponse.class);
            if (response == null || response.status() == null) {
                log.warn("PortOne 빌링키 조회 응답이 비어 있음");
                throw new BusinessException(CommonErrorCode.EXTERNAL_SERVICE_ERROR);
            }
            if (response.status() == PortOneBillingKeyStatus.UNKNOWN) {
                log.warn("PortOne 빌링키 상태를 해석할 수 없음");
                throw new BusinessException(CommonErrorCode.EXTERNAL_SERVICE_ERROR);
            }
            return Optional.of(PortOneBillingKeyInfo.from(response));
        } catch (HttpClientErrorException.NotFound e) {
            log.warn("PortOne에 빌링키가 없음");
            return Optional.empty();
        } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden e) {
            log.error("PortOne 빌링키 조회 인증·권한 오류: status={} — API Secret·상점 설정을 확인해야 한다",
                e.getStatusCode());
            throw new BusinessException(CommonErrorCode.EXTERNAL_SERVICE_ERROR);
        } catch (RestClientResponseException e) {
            log.warn("PortOne 빌링키 조회 오류 응답: status={}", e.getStatusCode());
            throw new BusinessException(CommonErrorCode.EXTERNAL_SERVICE_ERROR);
        } catch (ResourceAccessException e) {
            if (isTimeout(e)) {
                log.warn("PortOne 빌링키 조회 timeout");
                throw new BusinessException(CommonErrorCode.EXTERNAL_SERVICE_TIMEOUT);
            }
            log.warn("PortOne 빌링키 조회 연결 실패: {}", e.getClass().getSimpleName());
            throw new BusinessException(CommonErrorCode.EXTERNAL_SERVICE_ERROR);
        } catch (RestClientException e) {
            // 성공 응답 본문을 DTO로 읽지 못한 경우 등
            log.warn("PortOne 빌링키 조회 응답 처리 실패: {}", e.getClass().getSimpleName());
            throw new BusinessException(CommonErrorCode.EXTERNAL_SERVICE_ERROR);
        }
    }

    // 빌링키 청구(POST /payments/{paymentId}/billing-key). 응답을 예외로 던지지 않고 2단계 4-3 계약대로 분류해 돌려준다 —
    // FAILED는 만들지 않는다. 어떤 결과든 호출자가 같은 결제 ID로 GET /payments/{id}를 다시 조회해 확정한다.
    // 결제 ID가 PortOne 쪽 멱등 단위라 Idempotency-Key 헤더는 쓰지 않는다.
    // 요청 본문에 빌링키·고객 정보가 있으므로 본문·예외 객체·e.getMessage()를 로그·결과·cause 어디에도 넣지 않는다.
    // 오류 본문은 type만 읽고(기존 cancel()과 같은 방식) 허용 목록에 있을 때만 detail에 쓴다. pgMessage는 읽지 않는다
    public PortOneBillingPaymentResult payWithBillingKey(String paymentId, PortOneBillingPaymentRequest request) {
        try {
            PortOneBillingPaymentResult result = restClient.post()
                .uri("/payments/{paymentId}/billing-key", paymentId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                // 4xx·5xx도 예외가 아니라 분류 대상이라 retrieve() 대신 응답을 직접 받는다
                .exchange((httpRequest, response) -> {
                    int status = response.getStatusCode().value();
                    if (response.getStatusCode().is2xxSuccessful()) {
                        return PortOneBillingPaymentResult.from(readSuccessBody(response));
                    }
                    PortOneBillingPaymentResult classified =
                        PortOneBillingPaymentResult.fromError(status, errorTypeOf(response));
                    logError(paymentId, status, classified);
                    return classified;
                });
            if (result.outcome() == PortOneBillingPaymentOutcome.PAID) {
                log.info("PortOne 빌링키 청구 응답 PAID(재조회로 확정): paymentId={}", paymentId);
            } else if (result.outcome() == PortOneBillingPaymentOutcome.UNKNOWN && result.detail() != null
                && PortOneBillingPaymentResult.DETAIL_MISSING_FIELD.equals(result.detail())) {
                log.warn("PortOne 빌링키 청구 응답에 필수 필드가 없음: paymentId={}", paymentId);
            }
            return result;
        } catch (ResourceAccessException e) {
            if (isTimeout(e)) {
                log.warn("PortOne 빌링키 청구 timeout: paymentId={}", paymentId);
                return PortOneBillingPaymentResult.unknown(PortOneBillingPaymentResult.DETAIL_TIMEOUT);
            }
            log.warn("PortOne 빌링키 청구 연결 실패: paymentId={}, cause={}", paymentId, e.getClass().getSimpleName());
            return PortOneBillingPaymentResult.unknown(PortOneBillingPaymentResult.DETAIL_CONNECTION);
        } catch (RestClientException e) {
            // 성공 응답 본문을 DTO로 읽지 못한 경우 등. PortOne이 결제했는지 알 수 없다
            log.warn("PortOne 빌링키 청구 응답 처리 실패: paymentId={}, cause={}", paymentId, e.getClass().getSimpleName());
            return PortOneBillingPaymentResult.unknown(PortOneBillingPaymentResult.DETAIL_PARSE);
        }
    }

    // 결제 ID·HTTP 상태·허용 코드만. BILLING_KEY_UNUSABLE·REJECTED는 설정·키 상태 문제라 운영자가 봐야 한다
    private static void logError(String paymentId, int status, PortOneBillingPaymentResult result) {
        switch (result.outcome()) {
            case ALREADY_PAID -> log.info("PortOne 빌링키 청구: 같은 결제 ID가 이미 결제됨: paymentId={}", paymentId);
            case BILLING_KEY_UNUSABLE -> log.warn("PortOne 빌링키 청구: 사용할 수 없는 키: paymentId={}, status={}, detail={}",
                paymentId, status, result.detail());
            case REJECTED -> log.error("PortOne 빌링키 청구 거절(요청·설정 확인 필요): paymentId={}, status={}, detail={}",
                paymentId, status, result.detail());
            default -> log.warn("PortOne 빌링키 청구 결과 불명: paymentId={}, status={}, detail={}",
                paymentId, status, result.detail());
        }
    }

    // 성공 본문을 못 읽으면 null → MISSING_FIELD(UNKNOWN). 본문 해석 예외는 밖의 RestClientException 처리와 같은 결론이라
    // 여기서 삼킨다
    private static PortOneBillingPaymentResponse readSuccessBody(ConvertibleClientHttpResponse response) {
        try {
            return response.bodyTo(PortOneBillingPaymentResponse.class);
        } catch (RuntimeException e) {
            return null;
        }
    }

    // 오류 본문을 못 읽어도 분류는 계속한다. type이 없으면 상태 코드만으로 판단된다. message는 읽지 않는다
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
