package com.recipick.global.security;

import com.recipick.global.exception.AuthErrorCode;
import com.recipick.global.exception.BusinessException;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
public class LoginPrincipalArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(LoginMember.class)
                || parameter.hasParameterAnnotation(LoginAdmin.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
            NativeWebRequest request, WebDataBinderFactory binderFactory) {
        boolean memberParameter = parameter.hasParameterAnnotation(LoginMember.class);
        boolean adminParameter = parameter.hasParameterAnnotation(LoginAdmin.class);
        if (parameter.getParameterType() != Long.class || memberParameter == adminParameter) {
            throw new IllegalStateException("로그인 식별자는 하나의 인증 어노테이션을 붙인 Long 타입이어야 합니다.");
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AuthPrincipal principal)) {
            throw new BusinessException(AuthErrorCode.LOGIN_REQUIRED);
        }
        SubjectType expectedType = memberParameter ? SubjectType.MEMBER : SubjectType.ADMIN;
        if (principal.type() != expectedType) {
            throw new BusinessException(AuthErrorCode.ACCESS_DENIED);
        }
        return principal.id();
    }
}
