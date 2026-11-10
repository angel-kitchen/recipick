package com.recipick.global.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

// 폼 파라미터 대체를 허용하지 않고 브라우저 클라이언트의 명시적 헤더만 받는다.
public class AuthCsrfTokenRequestHandler extends CsrfTokenRequestAttributeHandler {
    @Override
    public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
        return request.getHeader(csrfToken.getHeaderName());
    }
}
