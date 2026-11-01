package com.recipick.infra.portone;

import com.recipick.domain.payment.exception.PaymentErrorCode;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.exception.CommonErrorCode;
import com.recipick.infra.portone.dto.PortOneCancelOutcome;
import com.recipick.infra.portone.dto.PortOneCancelRequest;
import com.recipick.infra.portone.dto.PortOneCancelResponse;
import com.recipick.infra.portone.dto.PortOneCancelResult;
import com.recipick.infra.portone.dto.PortOneErrorResponse;
import com.recipick.infra.portone.dto.PortOnePayment;
import com.recipick.infra.portone.dto.PortOnePaymentResponse;
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

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.TimeoutException;

//PortOne V2 결제 단건 조회와 전액 취소. 외부 호출만 담당하고 승인 가능 여부 같은 업무 판단은 domain Service가 한다.
// DB 트랜잭션 밖에서 호출해야 한다.

@Slf4j
@Component
public class PortOnePaymentClient {

    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final RestClient restClient;

    public PortOnePaymentClient(@Qualifier(PortOneClientConfig.REST_CLIENT_BEAN) RestClient restClient) {
        this.restClient = restClient;
    }

    public PortOnePayment getPayment(String portonePaymentId) {
        try {
            PortOnePaymentResponse response = restClient.get()
                .uri("/payments/{paymentId}", portonePaymentId)
                .retrieve()
                .body(PortOnePaymentResponse.class);
            if (response == null) {
                log.warn("PortOne 결제 조회 응답이 비어 있음: paymentId={}", portonePaymentId);
                throw new BusinessException(CommonErrorCode.EXTERNAL_SERVICE_ERROR);
            }
            return PortOnePayment.from(response);
        } catch (HttpClientErrorException.NotFound e) {
            log.warn("PortOne에 결제가 없음: paymentId={}", portonePaymentId);
            throw new BusinessException(PaymentErrorCode.PAYMENT_NOT_FOUND, e);
        } catch (RestClientResponseException e) {
            log.warn("PortOne 결제 조회 오류 응답: paymentId={}, status={}", portonePaymentId, e.getStatusCode());
            throw new BusinessException(CommonErrorCode.EXTERNAL_SERVICE_ERROR, e);
        } catch (ResourceAccessException e) {
            if (isTimeout(e)) {
                log.warn("PortOne 결제 조회 timeout: paymentId={}", portonePaymentId);
                throw new BusinessException(CommonErrorCode.EXTERNAL_SERVICE_TIMEOUT, e);
            }
            // 예외 객체는 남기지 않는다 — 원문(요청 URL·응답 일부)이 로그에 실릴 수 있다(3단계 김선생 코드 검수 ⑤)
            log.warn("PortOne 결제 조회 연결 실패: paymentId={}, code={}, cause={}", portonePaymentId,
                CommonErrorCode.EXTERNAL_SERVICE_ERROR.getCode(), e.getClass().getSimpleName());
            throw new BusinessException(CommonErrorCode.EXTERNAL_SERVICE_ERROR, e);
        } catch (RestClientException e) {
            // 응답 본문을 DTO로 읽지 못한 경우 등. 역직렬화 예외 메시지에는 응답 값 원문이 들어갈 수 있어 예외 객체를 남기지 않는다
            log.warn("PortOne 결제 조회 응답 처리 실패: paymentId={}, code={}, cause={}", portonePaymentId,
                CommonErrorCode.EXTERNAL_SERVICE_ERROR.getCode(), e.getClass().getSimpleName());
            throw new BusinessException(CommonErrorCode.EXTERNAL_SERVICE_ERROR, e);
        }
    }

    // 회원 취소. requester=CUSTOMER로 보낸다. 호출 규칙은 cancel()과 같다
    public PortOneCancelResult cancelPayment(
        String portonePaymentId, String idempotencyKey, String reason, Long cancellableAmount) {
        return cancel(portonePaymentId, idempotencyKey, PortOneCancelRequest.byCustomer(reason, cancellableAmount));
    }

    // SYSTEM 환불의 사후 반환과 마트 거절 환불. 고객 요청이 아니라 서버·마트 판단이라 requester=ADMIN으로 보낸다.
    // 여기의 ADMIN은 PG에 알리는 취소 요청 주체다. 레시픽 내부의 RefundRequester.ADMIN(마트 거절 환불)과는 다른 값이다.
    // 호출 규칙은 cancel()과 같다
    public PortOneCancelResult cancelPaymentByAdmin(
        String portonePaymentId, String idempotencyKey, String reason, Long cancellableAmount) {
        return cancel(portonePaymentId, idempotencyKey, PortOneCancelRequest.byAdmin(reason, cancellableAmount));
    }

    // PortOne 전액 취소를 호출하고, 응답을 예외로 던지지 않고 결과 상태로 분류해 반환한다.
    // 호출 전에 취소 요청 상태가 이미 커밋되므로, 어떤 응답이 와도 이후 TX2에서 상태를 정리할 수 있어야 한다.
    // idempotencyKey는 환불 행마다 고정된 서버 키이고, 본문의 currentCancellableAmount는 현재 취소 가능 잔액 검증에 사용한다.
    private PortOneCancelResult cancel(String portonePaymentId, String idempotencyKey, PortOneCancelRequest body) {
        try {
            PortOneCancelResult result = restClient.post()
                .uri("/payments/{paymentId}/cancel", portonePaymentId)
                .header(IDEMPOTENCY_KEY_HEADER, "\"" + idempotencyKey + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                // 4xx·5xx도 예외가 아니라 분류 대상이라 retrieve() 대신 응답을 직접 받는다
                .exchange((request, response) -> {
                    if (response.getStatusCode().is2xxSuccessful()) {
                        return PortOneCancelResult.from(response.bodyTo(PortOneCancelResponse.class));
                    }
                    return PortOneCancelResult.fromError(response.getStatusCode().value(), errorTypeOf(response));
                });
            // REQUESTED는 PortOne이 정의한 정상 비동기 처리, REJECTED는 우리 요청·인증 설정 문제라 운영자가 바로 봐야 한다
            if (result.outcome() == PortOneCancelOutcome.REQUESTED) {
                log.info("PortOne 결제 취소 처리 중: paymentId={}, cancellationId={}",
                    portonePaymentId, result.cancellationId());
            } else if (result.outcome() == PortOneCancelOutcome.REJECTED) {
                log.error("PortOne 결제 취소 요청 거절: paymentId={}, detail={}",
                    portonePaymentId, result.detail());
            } else if (result.outcome() != PortOneCancelOutcome.SUCCEEDED) {
                log.warn("PortOne 결제 취소 미완료: paymentId={}, outcome={}, detail={}",
                    portonePaymentId, result.outcome(), result.detail());
            }
            return result;
        } catch (ResourceAccessException e) {
            String detail = isTimeout(e) ? "timeout" : "연결 실패";
            log.warn("PortOne 결제 취소 {}: paymentId={}", detail, portonePaymentId);
            return PortOneCancelResult.unconfirmed(detail);
        } catch (RestClientException e) {
            // 성공 응답 본문을 DTO로 읽지 못한 경우 등. PortOne이 취소했는지 알 수 없다
            log.warn("PortOne 결제 취소 응답 처리 실패: paymentId={}", portonePaymentId, e);
            return PortOneCancelResult.unconfirmed("응답 처리 실패");
        }
    }

    // 오류 본문을 못 읽어도 분류는 계속한다. type이 없으면 상태 코드만으로 판단된다
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
