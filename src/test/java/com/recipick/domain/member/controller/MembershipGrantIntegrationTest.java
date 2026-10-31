package com.recipick.domain.member.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipick.IntegrationTestSupport;
import com.recipick.domain.member.entity.Admin;
import com.recipick.domain.member.entity.Member;
import com.recipick.domain.member.entity.Membership;
import com.recipick.domain.member.entity.enums.AdminRole;
import com.recipick.domain.member.entity.enums.MembershipSource;
import com.recipick.domain.member.entity.enums.MembershipType;
import com.recipick.domain.member.repository.AdminRepository;
import com.recipick.domain.member.repository.MemberRepository;
import com.recipick.domain.member.repository.MembershipRepository;
import com.recipick.domain.member.service.MembershipService;
import com.recipick.global.security.AuthPrincipal;
import com.recipick.global.security.JwtTokenProvider;
import com.recipick.global.security.SubjectType;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class MembershipGrantIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private MemberRepository members;
    @Autowired
    private AdminRepository admins;
    @Autowired
    private MembershipRepository memberships;
    @Autowired
    private MembershipService membershipService;
    @Autowired
    private JwtTokenProvider tokens;
    @Autowired
    private Clock clock;

    private Member member;
    private Admin admin;
    private String token;

    @BeforeEach
    void setUp() {
        member = members.saveAndFlush(Member.builder().email(UUID.randomUUID() + "@grant.test")
                .phone(UUID.randomUUID().toString().substring(0, 20)).name("지급 검증 회원").passwordHash("hash").build());
        admin = admin(AdminRole.SUPER_ADMIN, true);
        token = adminToken(admin, "SUPER_ADMIN", null);
    }

    @Test
    @DisplayName("슈퍼 관리자가 결제 없는 30일 이용권을 지급하면 지급 관리자와 생성 시각을 기록한다")
    void grantsThirtyDaysAndRecordsAdmin() throws Exception {
        // given
        LocalDateTime before = LocalDateTime.now(clock);

        // when
        MockHttpServletResponse response = grant(member.getId(), token);

        // then
        assertThat(response.getStatus()).isEqualTo(201);
        JsonNode body = body(response);
        JsonNode data = body.path("data");
        assertThat(body.path("code").asText()).isEqualTo("OK");
        assertThat(data.path("memberId").asLong()).isEqualTo(member.getId());
        assertThat(data.path("type").asText()).isEqualTo("PAID");
        assertThat(data.path("source").asText()).isEqualTo("ADMIN_GRANT");
        assertThat(data.path("grantedByAdminId").asLong()).isEqualTo(admin.getId());
        OffsetDateTime start = OffsetDateTime.parse(data.path("startAt").asText());
        OffsetDateTime end = OffsetDateTime.parse(data.path("endAt").asText());
        assertThat(Duration.between(start, end)).isEqualTo(Duration.ofDays(30));
        assertThat(start.getOffset().getTotalSeconds()).isEqualTo(9 * 3600);
        Membership saved = memberships.findById(data.path("membershipId").asLong()).orElseThrow();
        assertThat(saved.getActivatedAt()).isEqualTo(saved.getStartAt());
        assertThat(saved.getGraceEndAt()).isNull();
        assertThat(saved.getMember().getId()).isEqualTo(member.getId());
        assertThat(saved.getGrantedByAdmin().getId()).isEqualTo(admin.getId());
        assertThat(saved.getStartAt()).isBetween(before, LocalDateTime.now(clock));
        assertThat(saved.getEndAt()).isEqualTo(saved.getStartAt().plusDays(30));
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.isAutoRenew()).isFalse();
        assertThat(saved.getBillingAnchorDate()).isNull();
        assertThat(saved.getConsentedAmount()).isNull();
        assertThat(saved.getConsentTextVersion()).isNull();
        assertThat(saved.getAutopayConsentedAt()).isNull();
    }

    @Test
    @DisplayName("지급 직후 회원 조회와 내부 검증에서 활성 혜택을 사용할 수 있고 무료체험은 불가하다")
    void connectsGrantToMemberQueriesAndAccess() throws Exception {
        // given
        assertThat(membershipService.isActive(member.getId())).isFalse();
        assertThat(grant(member.getId(), token).getStatus()).isEqualTo(201);
        String memberToken = tokens.createMemberTokens(member.getId()).accessToken();

        // when
        MockHttpServletResponse membership = mockMvc.perform(get("/api/memberships/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken)).andReturn().getResponse();
        MockHttpServletResponse profile = mockMvc.perform(get("/api/members/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken)).andReturn().getResponse();
        membershipService.requireActiveMembership(member.getId());

        // then
        assertThat(membership.getStatus()).isEqualTo(200);
        assertThat(profile.getStatus()).isEqualTo(200);
        assertThat(body(membership).path("data").path("active").asBoolean()).isTrue();
        assertThat(body(membership).path("data").path("current").path("source").asText()).isEqualTo("ADMIN_GRANT");
        assertThat(body(membership).path("data").path("trialEligible").asBoolean()).isFalse();
        assertThat(body(profile).path("data").path("membership").path("active").asBoolean()).isTrue();
        assertThat(body(profile).path("data").path("trialEligible").asBoolean()).isFalse();
        assertThat(membershipService.isActive(member.getId())).isTrue();
    }

    @ParameterizedTest
    @CsvSource({"PAID,SUBSCRIBE", "TRIAL,TRIAL"})
    @DisplayName("결제 대기·전환 대기 이용권이 있으면 관리자 지급을 거절한다")
    void rejectsPendingMembership(MembershipType type, MembershipSource source) throws Exception {
        // given
        LocalDateTime start = LocalDateTime.now(clock).minusDays(8);
        memberships.saveAndFlush(Membership.builder().member(member).type(type).source(source)
                .startAt(start).activatedAt(type == MembershipType.TRIAL ? start : null)
                .endAt(start.plusDays(7)).autoRenew(true).build());
        // when
        MockHttpServletResponse response = grant(member.getId(), token);
        // then
        assertError(response, 409, "MEMBERSHIP-002");
    }

    @ParameterizedTest
    @CsvSource({"TRIAL,TRIAL", "PAID,SUBSCRIBE", "PAID,ADMIN_GRANT"})
    @DisplayName("활성 체험·유료·관리자 지급 이용권이 있으면 추가 지급하지 않는다")
    void rejectsActiveMembership(String type, String source) throws Exception {
        // given
        LocalDateTime now = LocalDateTime.now(clock);
        memberships.saveAndFlush(Membership.builder().member(member).grantedByAdmin(admin)
                .type(MembershipType.valueOf(type)).source(MembershipSource.valueOf(source))
                .startAt(now.minusDays(1)).activatedAt(now.minusDays(1)).endAt(now.plusDays(6)).build());

        // when
        MockHttpServletResponse response = grant(member.getId(), token);

        // then
        assertError(response, 409, "MEMBERSHIP-002");
        assertThat(memberships.findActiveMemberships(member.getId(), now, PageRequest.of(0, 10))).hasSize(1);
    }

    @Test
    @DisplayName("해지 예약된 이용권도 기간이 남아 있으면 추가 지급하지 않는다")
    void rejectsCancellationReservedMembership() throws Exception {
        // given
        LocalDateTime now = LocalDateTime.now(clock);
        Membership membership = Membership.builder().member(member).type(MembershipType.PAID)
                .source(MembershipSource.SUBSCRIBE).startAt(now.minusDays(1)).activatedAt(now.minusDays(1)).endAt(now.plusDays(29))
                .autoRenew(true).build();
        membership.reserveCancellation(now);
        memberships.saveAndFlush(membership);

        // when
        MockHttpServletResponse response = grant(member.getId(), token);

        // then
        assertError(response, 409, "MEMBERSHIP-002");
        assertThat(membership.isAutoRenew()).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"false", "true"})
    @DisplayName("만료 또는 즉시 종료된 이력만 있으면 새 이용권을 지급하고 기존 이력을 유지한다")
    void allowsExpiredOrTerminatedHistory(boolean terminated) throws Exception {
        // given
        LocalDateTime now = LocalDateTime.now(clock);
        Membership previous = Membership.builder().member(member).type(MembershipType.PAID)
                .source(MembershipSource.SUBSCRIBE).startAt(now.minusDays(31)).activatedAt(now.minusDays(31))
                .endAt(terminated ? now.plusDays(1) : now.minusDays(1)).build();
        if (terminated) {
            previous.terminateAfterRefund(now.minusHours(1));
        }
        memberships.saveAndFlush(previous);

        // when
        MockHttpServletResponse response = grant(member.getId(), token);

        // then
        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(memberships.existsById(previous.getId())).isTrue();
        assertThat(body(response).path("data").path("membershipId").asLong()).isNotEqualTo(previous.getId());
    }

    @Test
    @DisplayName("비로그인·회원·마트 관리자에게 지급 권한이 없다")
    void enforcesSuperAdminOnly() throws Exception {
        // given
        String memberToken = tokens.createMemberTokens(member.getId()).accessToken();
        Admin martAdmin = admin(AdminRole.MART_ADMIN, true);
        String martToken = adminToken(martAdmin, "MART_ADMIN", 1L);

        // when
        MockHttpServletResponse anonymous = grant(member.getId(), null);
        MockHttpServletResponse ordinary = grant(member.getId(), memberToken);
        MockHttpServletResponse mart = grant(member.getId(), martToken);

        // then
        assertError(anonymous, 401, "AUTH-001");
        assertError(ordinary, 403, "AUTH-004");
        assertError(mart, 403, "AUTH-006");
        assertThat(memberships.existsByMemberId(member.getId())).isFalse();
    }

    @Test
    @DisplayName("비활성 관리자와 DB에서 권한이 다른 관리자는 슈퍼 관리자 토큰이어도 지급하지 못한다")
    void validatesStoredAdminState() throws Exception {
        // given
        Admin inactive = admin(AdminRole.SUPER_ADMIN, false);
        Admin demoted = admin(AdminRole.MART_ADMIN, true);

        // when
        MockHttpServletResponse inactiveResponse = grant(member.getId(), adminToken(inactive, "SUPER_ADMIN", null));
        MockHttpServletResponse demotedResponse = grant(member.getId(), adminToken(demoted, "SUPER_ADMIN", null));
        MockHttpServletResponse missingAdmin = grant(member.getId(), tokens.createTokens(
                new AuthPrincipal(SubjectType.ADMIN, Long.MAX_VALUE, "SUPER_ADMIN", null)).accessToken());

        // then
        assertError(inactiveResponse, 403, "AUTH-009");
        assertError(demotedResponse, 403, "AUTH-006");
        assertError(missingAdmin, 403, "AUTH-004");
        assertThat(memberships.existsByMemberId(member.getId())).isFalse();
    }

    @Test
    @DisplayName("없는 회원에게 지급하면 MEMBER-001로 응답한다")
    void rejectsMissingMember() throws Exception {
        // given
        Long missingId = Long.MAX_VALUE;

        // when
        MockHttpServletResponse response = grant(missingId, token);

        // then
        assertError(response, 404, "MEMBER-001");
    }

    @Test
    @DisplayName("요청 본문의 관리자·대상·기간 값은 무시하고 토큰 관리자와 경로 회원에게 정해진 기간을 지급한다")
    void ignoresSuppliedGrantDetails() throws Exception {
        // given
        Map<String, Object> body = Map.of("adminId", Long.MAX_VALUE, "memberId", Long.MAX_VALUE, "days", 365);

        // when
        MockHttpServletResponse response = mockMvc.perform(post("/api/admin/members/{id}/memberships", member.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body))).andReturn().getResponse();

        // then
        assertThat(response.getStatus()).isEqualTo(201);
        JsonNode data = body(response).path("data");
        assertThat(data.path("memberId").asLong()).isEqualTo(member.getId());
        assertThat(data.path("grantedByAdminId").asLong()).isEqualTo(admin.getId());
        assertThat(Duration.between(OffsetDateTime.parse(data.path("startAt").asText()),
                OffsetDateTime.parse(data.path("endAt").asText()))).isEqualTo(Duration.ofDays(30));
    }

    private Admin admin(AdminRole role, boolean active) {
        return admins.saveAndFlush(Admin.builder().loginId(UUID.randomUUID().toString()).passwordHash("hash")
                .name("지급 검증 관리자").role(role).martId(role == AdminRole.MART_ADMIN ? 1L : null).active(active).build());
    }

    private String adminToken(Admin admin, String role, Long martId) {
        return tokens.createTokens(new AuthPrincipal(SubjectType.ADMIN, admin.getId(), role, martId)).accessToken();
    }

    private MockHttpServletResponse grant(Long memberId, String access) throws Exception {
        var request = post("/api/admin/members/{id}/memberships", memberId);
        if (access != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + access);
        }
        return mockMvc.perform(request).andReturn().getResponse();
    }

    private JsonNode body(MockHttpServletResponse response) throws Exception {
        return mapper.readTree(response.getContentAsString());
    }

    private void assertError(MockHttpServletResponse response, int status, String code) throws Exception {
        assertThat(response.getStatus()).isEqualTo(status);
        assertThat(body(response).path("code").asText()).isEqualTo(code);
    }
}
