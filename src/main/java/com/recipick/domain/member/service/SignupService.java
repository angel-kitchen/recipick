package com.recipick.domain.member.service;

import com.recipick.domain.member.dto.SignupRequest;
import com.recipick.domain.member.dto.SignupResponse;
import com.recipick.domain.member.entity.Member;
import com.recipick.domain.member.exception.MemberErrorCode;
import com.recipick.domain.member.repository.MemberRepository;
import com.recipick.global.common.TimeConstants;
import com.recipick.global.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SignupService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        if (!Boolean.TRUE.equals(request.termsAgreed()) || !Boolean.TRUE.equals(request.privacyAgreed())) {
            throw new BusinessException(MemberErrorCode.REQUIRED_AGREEMENT_MISSING);
        }
        if (memberRepository.existsByEmail(request.email())) {
            throw new BusinessException(MemberErrorCode.DUPLICATE_EMAIL);
        }
        if (memberRepository.existsByPhone(request.phone())) {
            throw new BusinessException(MemberErrorCode.DUPLICATE_PHONE);
        }
        LocalDateTime agreedAt = LocalDateTime.now(TimeConstants.ZONE_ID);
        Member member = Member.builder()
                .email(request.email())
                .name(request.name())
                .phone(request.phone())
                .passwordHash(passwordEncoder.encode(request.password()))
                .termsAgreedAt(agreedAt)
                .privacyAgreedAt(agreedAt)
                .build();
        try {
            return SignupResponse.from(memberRepository.saveAndFlush(member));
        } catch (DataIntegrityViolationException exception) {
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof ConstraintViolationException violation && violation.getConstraintName() != null) {
                    String constraint = violation.getConstraintName().toLowerCase(Locale.ROOT);
                    if (constraint.contains("uk_member_email")) {
                        throw new BusinessException(MemberErrorCode.DUPLICATE_EMAIL, exception);
                    }
                    if (constraint.contains("uk_member_phone")) {
                        throw new BusinessException(MemberErrorCode.DUPLICATE_PHONE, exception);
                    }
                }
            }
            throw exception;
        }
    }
}
