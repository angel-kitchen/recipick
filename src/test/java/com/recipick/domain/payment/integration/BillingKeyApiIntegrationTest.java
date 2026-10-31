package com.recipick.domain.payment.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipick.IntegrationTestSupport;
import com.recipick.domain.member.entity.Member;
import com.recipick.domain.member.repository.MemberRepository;
import com.recipick.domain.payment.entity.BillingKey;
import com.recipick.domain.payment.entity.enums.BillingKeyStatus;
import com.recipick.domain.payment.repository.BillingKeyRepository;
import com.recipick.global.security.JwtTokenProvider;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

// 카드 등록 API 중 PortOne이 끼지 않는 경로를 실제 서블릿 스택(MockMvc)과 MySQL로 본다 — 발급 요청, 내 카드 조회, 입력 검증.
// PortOne 조회가 필요한 POST /api/billing-keys의 성공 경로는 실연동 절차 R-1(테스트 채널)로 확인한다
class BillingKeyApiIntegrationTest extends IntegrationTestSupport {

    private static final String PASSWORD = "Original1!";

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    MemberRepository members;

    @Autowired
    BillingKeyRepository billingKeys;

    @Autowired
    JwtTokenProvider tokens;

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    Clock clock;

    private Member member;
    private String access;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString();
        member = members.saveAndFlush(Member.builder().email(suffix + "@t.dev").phone(suffix.substring(0, 20))
            .name("빌링키API테스트").passwordHash(encoder.encode(PASSWORD)).build());
        access = tokens.createMemberTokens(member.getId()).accessToken();
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM billing_key_issue_request WHERE member_id = ?", member.getId());
        jdbc.update("DELETE FROM billing_key WHERE member_id = ?", member.getId());
        members.deleteById(member.getId());
    }

    @Test
    @DisplayName("발급 요청은 SDK에 넣을 값 8개를 돌려주고, 행을 REQUESTED로 남기며, 응답을 캐시하지 말라고 표시한다")
    void issuesRequest() throws Exception {
        // when
        String body = mvc.perform(post("/api/billing-keys/issue-requests")
                .header("Authorization", "Bearer " + access))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", containsString("no-store")))
            .andExpect(jsonPath("$.code").value("OK"))
            .andExpect(jsonPath("$.data.issueId").isString())
            .andExpect(jsonPath("$.data.issueName").value("레시픽 멤버십 자동결제 카드 등록"))
            .andExpect(jsonPath("$.data.storeId").value("test-store-id"))
            .andExpect(jsonPath("$.data.channelKey").value("test-channel-key"))
            .andExpect(jsonPath("$.data.customerId").value(String.valueOf(member.getId())))
            .andExpect(jsonPath("$.data.displayAmount").value(3900))
            .andExpect(jsonPath("$.data.currency").value("KRW"))
            .andExpect(jsonPath("$.data.expiresAt").isString())
            .andReturn().getResponse().getContentAsString();

        // then
        JsonNode data = objectMapper.readTree(body).get("data");
        String issueId = data.get("issueId").asText();
        assertThat(issueId).startsWith("rcp-bk-");
        Map<String, Object> row = jdbc.queryForMap(
            "SELECT member_id, status, billing_key_id FROM billing_key_issue_request WHERE issue_id = ?", issueId);
        assertThat(row.get("member_id")).isEqualTo(member.getId());
        assertThat(row.get("status")).isEqualTo("REQUESTED");
        assertThat(row.get("billing_key_id")).isNull();
        // DATETIME은 드라이버 설정에 따라 Timestamp 또는 LocalDateTime으로 오므로 타입을 명시해 읽는다
        LocalDateTime expiresAt = jdbc.queryForObject(
            "SELECT expires_at FROM billing_key_issue_request WHERE issue_id = ?",
            (rs, rowNum) -> rs.getObject("expires_at", LocalDateTime.class), issueId);
        LocalDateTime now = LocalDateTime.now(clock);
        assertThat(expiresAt).isBetween(now.plusMinutes(29), now.plusMinutes(31));
    }

    @Test
    @DisplayName("발급 요청은 매번 다른 토큰을 만든다")
    void issuesDistinctTokens() throws Exception {
        // when
        String first = issue();
        String second = issue();

        // then
        assertThat(first).isNotEqualTo(second);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM billing_key_issue_request WHERE member_id = ?",
            Long.class, member.getId())).isEqualTo(2L);
    }

    @Test
    @DisplayName("등록 전 내 카드 조회는 registered=false이고 나머지는 null이다")
    void getMineBeforeRegistration() throws Exception {
        mvc.perform(get("/api/billing-keys/me").header("Authorization", "Bearer " + access))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.registered").value(false))
            .andExpect(jsonPath("$.data.billingKeyId").isEmpty())
            .andExpect(jsonPath("$.data.status").isEmpty())
            .andExpect(jsonPath("$.data.issuedAt").isEmpty());
    }

    @Test
    @DisplayName("ACTIVE 키가 있으면 내 카드 조회는 등록됨·ID·상태·등록일을 주고 키 원문은 주지 않는다")
    void getMineAfterRegistration() throws Exception {
        // given
        String key = "billing-key-test-" + UUID.randomUUID();
        BillingKey active = billingKeys.saveAndFlush(BillingKey.builder()
            .memberId(member.getId()).portoneBillingKey(key).status(BillingKeyStatus.ACTIVE)
            .issuedAt(LocalDateTime.of(2026, 10, 31, 14, 0)).build());

        // when
        String body = mvc.perform(get("/api/billing-keys/me").header("Authorization", "Bearer " + access))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.registered").value(true))
            .andExpect(jsonPath("$.data.billingKeyId").value(active.getId()))
            .andExpect(jsonPath("$.data.status").value("ACTIVE"))
            .andExpect(jsonPath("$.data.issuedAt").value("2026-10-31T14:00:00+09:00"))
            .andReturn().getResponse().getContentAsString();

        // then
        assertThat(body).doesNotContain(key);
    }

    @Test
    @DisplayName("REPLACED 키만 있으면 내 카드 조회는 등록 전과 같다")
    void getMineIgnoresReplacedKey() throws Exception {
        // given
        billingKeys.saveAndFlush(BillingKey.builder()
            .memberId(member.getId()).portoneBillingKey("billing-key-test-" + UUID.randomUUID())
            .status(BillingKeyStatus.REPLACED).issuedAt(LocalDateTime.of(2026, 10, 31, 14, 0)).build());

        // then
        mvc.perform(get("/api/billing-keys/me").header("Authorization", "Bearer " + access))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.registered").value(false));
    }

    @Test
    @DisplayName("등록 본문이 비어 있으면 PortOne을 부르기 전에 400 COMMON-001이다")
    void rejectsBlankRegisterRequest() throws Exception {
        mvc.perform(post("/api/billing-keys")
                .header("Authorization", "Bearer " + access)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"issueId\":\"\",\"billingKey\":\" \"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("COMMON-001"));
    }

    @Test
    @DisplayName("없는 토큰으로 등록하면 PortOne을 부르기 전에 409 BILLING-007이다")
    void rejectsUnknownToken() throws Exception {
        mvc.perform(post("/api/billing-keys")
                .header("Authorization", "Bearer " + access)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"issueId\":\"rcp-bk-" + UUID.randomUUID() + "\",\"billingKey\":\"billing-key-test-x\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("BILLING-007"));
    }

    @Test
    @DisplayName("토큰 없이 부르면 401이다 — 회원 API다")
    void requiresLogin() throws Exception {
        mvc.perform(post("/api/billing-keys/issue-requests"))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/billing-keys/me"))
            .andExpect(status().isUnauthorized());
    }

    private String issue() throws Exception {
        String body = mvc.perform(post("/api/billing-keys/issue-requests")
                .header("Authorization", "Bearer " + access))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("data").get("issueId").asText();
    }
}
