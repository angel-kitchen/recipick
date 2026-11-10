package com.recipick.domain.payment.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recipick.IntegrationTestSupport;
import com.recipick.domain.member.entity.Member;
import com.recipick.domain.member.repository.MemberRepository;
import com.recipick.domain.payment.entity.BillingKeyIssueRequest;
import com.recipick.domain.payment.service.BillingKeyIssueRequestService;
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

// 카드 등록 전용 채널 키가 비어 있는 설정. 주문 일반결제 채널 키(test-channel-key)는 그대로 있으므로, 여기서 등록 API가
// 열리면 일반결제 키로 대신한 것이다. 플래그가 켜진 채 키만 없어도 컨텍스트는 뜬다 — 운영이 새 키 없이 기동하는 조건과 같다
@TestPropertySource(properties = "portone.billing-channel-key=")
class BillingKeyRegistrationConfigMissingApiTest extends IntegrationTestSupport {

    private static final String PASSWORD = "Original1!";

    @Autowired
    MockMvc mvc;

    @Autowired
    MemberRepository members;

    @Autowired
    BillingKeyIssueRequestService issueRequests;

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
            .name("빌링키채널설정테스트").passwordHash(encoder.encode(PASSWORD)).build());
        access = testMemberTokens(member.getId()).accessToken();
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM billing_key_issue_request WHERE member_id = ?", member.getId());
        members.deleteById(member.getId());
    }

    @Test
    @DisplayName("카드 등록 채널 키가 비어 있으면 발급 요청은 500 COMMON-008이고 행을 만들지 않는다 — 일반결제 키로 대신하지 않는다")
    void issueRequestRejectsMissingBillingChannelKey() throws Exception {
        mvc.perform(post("/api/billing-keys/issue-requests").header("Authorization", "Bearer " + access))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("COMMON-008"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM billing_key_issue_request WHERE member_id = ?",
            Long.class, member.getId())).isZero();
    }

    @Test
    @DisplayName("카드 등록 채널 키가 비어 있으면 등록은 토큰이 맞아도 PortOne 조회 전에 500 COMMON-008이다")
    void registerRejectsMissingBillingChannelKey() throws Exception {
        // given
        BillingKeyIssueRequest request = issueRequests.create(member.getId());

        // when, then — PortOne 주소가 닫혀 있어(localhost:0) 조회를 시도했다면 COMMON-008이 아닌 다른 실패가 난다
        mvc.perform(post("/api/billing-keys")
                .header("Authorization", "Bearer " + access)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"issueId\":\"" + request.getIssueId() + "\",\"billingKey\":\"billing-key-test-x\"}"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("COMMON-008"));
    }

    @Test
    @DisplayName("주문 결제 공개 설정은 카드 등록 채널 키와 무관하게 일반결제 채널 키를 준다")
    void publicConfigKeepsOrderChannelKey() throws Exception {
        mvc.perform(get("/api/config/portone"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.storeId").value("test-store-id"))
            .andExpect(jsonPath("$.data.channelKey").value("test-channel-key"));
    }
}
