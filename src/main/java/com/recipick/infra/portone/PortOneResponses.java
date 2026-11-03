package com.recipick.infra.portone;

import com.recipick.infra.portone.dto.PortOneErrorResponse;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.TimeoutException;

// 결제·빌링·멤버십 환불 클라이언트가 같은 기준으로 PortOne 오류와 타임아웃을 분류하도록 한 곳에 둔다
final class PortOneResponses {

    private PortOneResponses() {
    }

    // 오류 본문을 못 읽어도 분류는 계속한다. type이 없으면 상태 코드만으로 판단된다. message는 읽지 않는다
    static String errorTypeOf(ConvertibleClientHttpResponse response) {
        try {
            PortOneErrorResponse error = response.bodyTo(PortOneErrorResponse.class);
            return error == null ? null : error.type();
        } catch (RuntimeException e) {
            return null;
        }
    }

    static boolean isTimeout(ResourceAccessException e) {
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
