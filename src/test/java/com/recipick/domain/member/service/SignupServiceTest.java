package com.recipick.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.recipick.domain.member.dto.SignupRequest;
import com.recipick.domain.member.entity.Member;
import com.recipick.domain.member.exception.MemberErrorCode;
import com.recipick.domain.member.repository.MemberRepository;
import com.recipick.global.exception.BusinessException;
import java.sql.SQLException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class SignupServiceTest {

    @Mock
    MemberRepository memberRepository;
    @Mock
    PasswordEncoder passwordEncoder;
    @Mock
    DuplicateMemberChecker duplicateMemberChecker;
    @InjectMocks
    SignupService signupService;

    private SignupRequest request() {
        return new SignupRequest("signup@example.com", "홍길동", "Abcd1234!", "01012345678", true, true);
    }

    @ParameterizedTest
    @DisplayName("명시된 UNIQUE 제약 위반을 회원 중복 오류로 변환한다")
    @CsvSource({"uk_member_email,DUPLICATE_EMAIL", "uk_member_phone,DUPLICATE_PHONE"})
    void translatesNamedConstraint(String constraint, MemberErrorCode expected) {
        // given
        when(passwordEncoder.encode("Abcd1234!")).thenReturn("hash");
        when(memberRepository.saveAndFlush(any(Member.class))).thenThrow(new DataIntegrityViolationException(
                "duplicate", new ConstraintViolationException("duplicate", new SQLException(), constraint)));
        // when / then
        assertThatThrownBy(() -> signupService.signup(request()))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(expected));
    }

    @ParameterizedTest
    @DisplayName("기존 자동 생성 제약 이름은 새 트랜잭션에서 중복을 재확인한다")
    @CsvSource({"23000,1062,DUPLICATE_EMAIL", "23000,1062,DUPLICATE_PHONE", "23505,23505,DUPLICATE_EMAIL"})
    void translatesLegacyConstraint(String state, int code, MemberErrorCode expected) {
        // given
        when(passwordEncoder.encode("Abcd1234!")).thenReturn("hash");
        when(memberRepository.saveAndFlush(any(Member.class))).thenThrow(new DataIntegrityViolationException(
                "duplicate", new ConstraintViolationException("duplicate",
                        new SQLException("duplicate", state, code), "UK_legacy_generated")));
        when(duplicateMemberChecker.findConflict("signup@example.com", "01012345678")).thenReturn(expected);
        // when / then
        assertThatThrownBy(() -> signupService.signup(request()))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(expected));
    }

    @Test
    @DisplayName("중복이 아닌 무결성 오류는 회원 중복으로 바꾸지 않는다")
    void preservesOtherIntegrityFailures() {
        // given
        when(passwordEncoder.encode("Abcd1234!")).thenReturn("hash");
        DataIntegrityViolationException failure = new DataIntegrityViolationException(
                "not null", new SQLException("not null", "23000", 1048));
        when(memberRepository.saveAndFlush(any(Member.class))).thenThrow(failure);
        // when / then
        assertThatThrownBy(() -> signupService.signup(request())).isSameAs(failure);
        verify(duplicateMemberChecker, never()).findConflict(any(), any());
    }
}
