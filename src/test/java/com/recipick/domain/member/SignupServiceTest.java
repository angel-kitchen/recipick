package com.recipick.domain.member;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.recipick.domain.member.dto.SignupRequest;
import com.recipick.domain.member.entity.Member;
import com.recipick.domain.member.exception.MemberErrorCode;
import com.recipick.domain.member.repository.MemberRepository;
import com.recipick.domain.member.service.SignupService;
import com.recipick.global.exception.BusinessException;
import java.sql.SQLException;
import org.hibernate.exception.ConstraintViolationException;
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

    @Mock MemberRepository memberRepository;
    @Mock PasswordEncoder passwordEncoder;
    @InjectMocks SignupService signupService;

    @ParameterizedTest
    @CsvSource({"uk_member_email,DUPLICATE_EMAIL", "uk_member_phone,DUPLICATE_PHONE"})
    void translatesDatabaseDuplicateAfterPrecheck(String constraint, MemberErrorCode expected) {
        when(passwordEncoder.encode("Abcd1234!")).thenReturn("hash");
        when(memberRepository.saveAndFlush(any(Member.class))).thenThrow(new DataIntegrityViolationException(
                "duplicate", new ConstraintViolationException("duplicate", new SQLException(), constraint)));
        SignupRequest request = new SignupRequest("signup@example.com", "홍길동", "Abcd1234!",
                "01012345678", true, true);
        assertThatThrownBy(() -> signupService.signup(request))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> org.assertj.core.api.Assertions.assertThat(exception.getErrorCode())
                                .isEqualTo(expected));
    }
}
