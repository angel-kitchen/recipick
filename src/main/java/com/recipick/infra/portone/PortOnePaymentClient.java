package com.recipick.infra.portone;

import com.recipick.domain.payment.exception.PaymentErrorCode;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.exception.CommonErrorCode;
import com.recipick.infra.portone.dto.PortOnePayment;
import com.recipick.infra.portone.dto.PortOnePaymentResponse;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * PortOne V2 결제 단건 조회. 외부 호출만 담당하고 승인 가능 여부 같은 업무 판단은 domain Service가 한다.
 * DB 트랜잭션 밖에서 호출해야 한다.
 */
@Slf4j
@Component
public class PortOnePaymentClient {

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
            log.warn("PortOne 결제 조회 연결 실패: paymentId={}", portonePaymentId, e);
            throw new BusinessException(CommonErrorCode.EXTERNAL_SERVICE_ERROR, e);
        } catch (RestClientException e) {
            // 응답 본문을 DTO로 읽지 못한 경우 등
            log.warn("PortOne 결제 조회 응답 처리 실패: paymentId={}", portonePaymentId, e);
            throw new BusinessException(CommonErrorCode.EXTERNAL_SERVICE_ERROR, e);
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
