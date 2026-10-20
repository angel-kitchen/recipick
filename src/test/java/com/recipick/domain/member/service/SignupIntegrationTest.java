package com.recipick.domain.member.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipick.IntegrationTestSupport;
import com.recipick.domain.member.entity.Member;
import com.recipick.domain.member.repository.MemberRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


class SignupIntegrationTest extends IntegrationTestSupport {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    MemberRepository memberRepository;
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    JdbcTemplate jdbcTemplate;

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
    @DisplayName("회원가입 시 비밀번호 해시와 동의 일시를 저장한다")
    void createsMemberWithHashAndAgreementTimes() throws Exception {
        // given
        Map<String, Object> body = request();
        // when / then
        mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
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
    @DisplayName("입력 형식이 잘못되면 가입하지 않는다")
    void rejectsInvalidInput(String field, String value) throws Exception {
        // given
        Map<String, Object> body = request();
        body.put(field, value);
        // when / then
        mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-001"));
        assertThat(memberRepository.count()).isZero();
    }

    @ParameterizedTest
    @CsvSource({"termsAgreed", "privacyAgreed"})
    @DisplayName("필수 약관에 동의하지 않으면 가입하지 않는다")
    void rejectsMissingAgreement(String field) throws Exception {
        // given
        Map<String, Object> body = request();
        body.put(field, false);
        // when / then
        mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MEMBER-004"));
        assertThat(memberRepository.count()).isZero();
    }

    @Test
    @DisplayName("동시에 가입해도 회원은 한 명만 생성된다")
    void concurrentSignupsCreateOnlyOneMember() throws Exception {
        // given
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
        // when
        try {
            Future<Integer> first = executor.submit(signup);
            Future<Integer> second = executor.submit(signup);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            // then
            assertThat(new Integer[]{first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)})
                    .containsExactlyInAnyOrder(201, 409);
            assertThat(memberRepository.count()).isEqualTo(1);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"email", "phone"})
    @EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = "jdbc:mysql:.*")
    @DisplayName("기존 MySQL UNIQUE 이름에서도 동시 가입 충돌을 409로 변환한다")
    void concurrentSignupsWithLegacyMysqlIndexes(String duplicateField) throws Exception {
        // given: 기존 DB의 자동 생성 제약 이름을 재현한다.
        jdbcTemplate.execute("ALTER TABLE member RENAME INDEX uk_member_email TO legacy_email_auto");
        jdbcTemplate.execute("ALTER TABLE member RENAME INDEX uk_member_phone TO legacy_phone_auto");
        CountDownLatch checked = new CountDownLatch(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            doAnswer(invocation -> {
                checked.countDown();
                if (!checked.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("동시 가입 사전 검사 대기 시간 초과");
                }
                return invocation.callRealMethod();
            }).when(testPasswordEncoder).encode(anyString());
            Map<String, Object> firstBody = request();
            Map<String, Object> secondBody = request();
            if (duplicateField.equals("email")) {
                secondBody.put("phone", "01099998888");
            } else {
                secondBody.put("email", "other@example.com");
            }
            Callable<MockHttpServletResponse> firstRequest = () -> mockMvc.perform(
                    post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(firstBody)))
                    .andReturn().getResponse();
            Callable<MockHttpServletResponse> secondRequest = () -> mockMvc.perform(
                    post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(secondBody)))
                    .andReturn().getResponse();
            // when: 두 요청 모두 사전 중복 검사를 통과한 뒤 INSERT한다.
            var first = executor.submit(firstRequest);
            var second = executor.submit(secondRequest);
            var firstResponse = first.get(20, TimeUnit.SECONDS);
            var secondResponse = second.get(20, TimeUnit.SECONDS);
            // then
            assertThat(new Integer[]{firstResponse.getStatus(), secondResponse.getStatus()})
                    .containsExactlyInAnyOrder(201, 409);
            var conflict = firstResponse.getStatus() == 409 ? firstResponse : secondResponse;
            assertThat(objectMapper.readTree(conflict.getContentAsString()).get("code").asText())
                    .isEqualTo(duplicateField.equals("email") ? "MEMBER-002" : "MEMBER-003");
            assertThat(memberRepository.count()).isEqualTo(1);
        } finally {
            checked.countDown();
            checked.countDown();
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
            reset(testPasswordEncoder);
            jdbcTemplate.execute("ALTER TABLE member RENAME INDEX legacy_email_auto TO uk_member_email");
            jdbcTemplate.execute("ALTER TABLE member RENAME INDEX legacy_phone_auto TO uk_member_phone");
        }
    }

    @Test
    @DisplayName("기존 대문자 이메일과 NULL 데이터가 있어도 가입 정책을 유지한다")
    void supportsLegacyMemberData() throws Exception {
        // given: 기존 엔티티에서 허용하던 NULL과 대문자 이메일을 재현한다.
        memberRepository.saveAndFlush(Member.builder()
                .email("SIGNUP@EXAMPLE.COM").phone("01011112222").build());
        // when / then
        mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEMBER-002"));
        assertThat(memberRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("이메일의 앞뒤 공백과 대소문자를 정규화해 저장한다")
    void normalizesEmail() throws Exception {
        // given
        Map<String, Object> body = request();
        body.put("email", "  Signup@Example.COM  ");
        // when / then
        mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.email").value("signup@example.com"));
        body.put("email", "SIGNUP@EXAMPLE.COM");
        body.put("phone", "01099998888");
        mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEMBER-002"));
        assertThat(memberRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("필수 동의가 누락되면 입력 오류를 반환한다")
    void rejectsOmittedAgreement() throws Exception {
        // given
        Map<String, Object> body = request();
        body.remove("termsAgreed");
        // when / then
        mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-001"));
    }

    @ParameterizedTest
    @CsvSource({"email,MEMBER-002", "phone,MEMBER-003"})
    @DisplayName("이미 가입한 이메일 또는 전화번호는 중복 오류를 반환한다")
    void rejectsDuplicate(String field, String code) throws Exception {
        // when / then
        mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request())))
                .andExpect(status().isCreated());
        // given
        Map<String, Object> body = request();
        if (field.equals("email")) {
            body.put("phone", "01099998888");
        } else {
            body.put("email", "other@example.com");
        }
        // when / then
        mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(code));
        assertThat(memberRepository.count()).isEqualTo(1);
    }
}
