package com.recipick.infra.portone;

import com.recipick.global.exception.BusinessException;
import com.recipick.global.exception.CommonErrorCode;
import com.recipick.infra.portone.dto.PortOneBillingKeyInfo;
import com.recipick.infra.portone.dto.PortOneBillingKeyInfoResponse;
import com.recipick.infra.portone.dto.PortOneBillingKeyStatus;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

// PortOne V2 빌링키 단건 조회. 외부 호출만 담당하고 등록 가능 여부 같은 업무 판단은 domain이 한다. DB 트랜잭션 밖에서 호출한다.
// 빌링키 원문은 요청 URL 경로에 들어간다. 그래서 예외 객체(메시지에 URL이 있다)를 로그에 남기지 않고 BusinessException의
// cause로도 넘기지 않는다. 로그에는 HTTP 상태와 분류만 남긴다
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
