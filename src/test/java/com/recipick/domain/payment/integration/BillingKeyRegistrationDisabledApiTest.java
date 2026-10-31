package com.recipick.domain.payment.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recipick.IntegrationTestSupport;
import com.recipick.domain.member.entity.Member;
import com.recipick.domain.member.repository.MemberRepository;
import com.recipick.global.security.JwtTokenProvider;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

// 운영 기본값(registration-enabled=false)에서 등록 API 두 개가 닫혀 있고 조회는 열려 있는지 본다.
// 플래그는 보조 스위치다 — 실채널 차단은 BillingKeyIssuanceVerifier가 코드로 한다(단위 테스트)
@TestPropertySource(properties = "recipick.billing-key.registration-enabled=false")
class BillingKeyRegistrationDisabledApiTest extends IntegrationTestSupport {

    private static final String PASSWORD = "Original1!";

    @Autowired
    MockMvc mvc;

    @Autowired
    MemberRepository members;

    @Autowired
    JwtTokenProvider tokens;

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    JdbcTemplate jdbc;

    private Member member;
    private String access;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString();
        member = members.saveAndFlush(Member.builder().email(suffix + "@t.dev").phone(suffix.substring(0, 20))
            .name("빌링키플래그테스트").passwordHash(encoder.encode(PASSWORD)).build());
        access = tokens.createMemberTokens(member.getId()).accessToken();
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM billing_key_issue_request WHERE member_id = ?", member.getId());
        members.deleteById(member.getId());
    }

    @Test
    @DisplayName("플래그가 꺼져 있으면 발급 요청은 503 BILLING-008이고 행을 만들지 않는다")
    void issueRequestIsDisabled() throws Exception {
        mvc.perform(post("/api/billing-keys/issue-requests").header("Authorization", "Bearer " + access))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("BILLING-008"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM billing_key_issue_request WHERE member_id = ?",
            Long.class, member.getId())).isZero();
    }

    @Test
    @DisplayName("플래그가 꺼져 있으면 등록은 입력이 맞아도 503 BILLING-008이다")
    void registerIsDisabled() throws Exception {
        mvc.perform(post("/api/billing-keys")
                .header("Authorization", "Bearer " + access)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"issueId\":\"rcp-bk-" + UUID.randomUUID() + "\",\"billingKey\":\"billing-key-test-x\"}"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("BILLING-008"));
    }

    @Test
    @DisplayName("플래그와 무관하게 내 카드 조회는 200이다")
    void getMineStaysOpen() throws Exception {
        mvc.perform(get("/api/billing-keys/me").header("Authorization", "Bearer " + access))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.registered").value(false));
    }
}
