package com.recipick.domain.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipick.domain.member.entity.Member;
import com.recipick.domain.member.repository.MemberRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Encoders;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SignupIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired MemberRepository memberRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        String secret = Encoders.BASE64.encode(Jwts.SIG.HS256.key().build().getEncoded());
        registry.add("security.jwt.secret", () -> secret);
    }

    @BeforeEach
    void clearMembers() {
        memberRepository.deleteAll();
    }

    private Map<String, Object> request() {
        return new HashMap<>(Map.of(
                "email", "signup@example.com", "name", "홍길동", "password", "Abcd1234!",
                "phone", "010-1234-5678", "termsAgreed", true, "privacyAgreed", true
        ));
    }

    @Test
    void createsMemberWithHashAndAgreementTimes() throws Exception {
        mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("CREATED"))
                .andExpect(jsonPath("$.message").value("회원가입이 완료되었습니다."))
                .andExpect(jsonPath("$.data.memberId").isNumber())
                .andExpect(jsonPath("$.data.email").value("signup@example.com"))
                .andExpect(jsonPath("$.data.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
        Member member = memberRepository.findAll().get(0);
        assertThat(member.getPhone()).isEqualTo("01012345678");
        assertThat(member.getPasswordHash()).isNotEqualTo("Abcd1234!");
        assertThat(passwordEncoder.matches("Abcd1234!", member.getPasswordHash())).isTrue();
        assertThat(member.getTermsAgreedAt()).isNotNull().isEqualTo(member.getPrivacyAgreedAt());
        assertThat(member.getMartId()).isNull();
        assertThat(member.getLocationAgreedAt()).isNull();
    }

    @ParameterizedTest
    @CsvSource({"email,invalid", "name,' '", "password,abcdefgh", "password,Abcd1234",
            "password,Abcd123! x", "phone,010abcd1234"})
    void rejectsInvalidInput(String field, String value) throws Exception {
        Map<String, Object> body = request();
        body.put(field, value);
        mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-001"));
        assertThat(memberRepository.count()).isZero();
    }

    @ParameterizedTest
    @CsvSource({"termsAgreed", "privacyAgreed"})
    void rejectsMissingAgreement(String field) throws Exception {
        Map<String, Object> body = request();
        body.put(field, false);
        mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MEMBER-004"));
        assertThat(memberRepository.count()).isZero();
    }

    @Test
    void concurrentSignupsCreateOnlyOneMember() throws Exception {
        String body = objectMapper.writeValueAsString(request());
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Integer> signup = () -> {
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("동시 요청 시작 대기 시간 초과");
            }
            return mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                    .content(body)).andReturn().getResponse().getStatus();
        };
        try {
            Future<Integer> first = executor.submit(signup);
            Future<Integer> second = executor.submit(signup);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(new Integer[]{first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)})
                    .containsExactlyInAnyOrder(201, 409);
            assertThat(memberRepository.count()).isEqualTo(1);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void rejectsOmittedAgreement() throws Exception {
        Map<String, Object> body = request();
        body.remove("termsAgreed");
        mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-001"));
    }

    @ParameterizedTest
    @CsvSource({"email,MEMBER-002", "phone,MEMBER-003"})
    void rejectsDuplicate(String field, String code) throws Exception {
        mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request())))
                .andExpect(status().isCreated());
        Map<String, Object> body = request();
        if (field.equals("email")) {
            body.put("phone", "01099998888");
        } else {
            body.put("email", "other@example.com");
        }
        mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(code));
        assertThat(memberRepository.count()).isEqualTo(1);
    }
}
