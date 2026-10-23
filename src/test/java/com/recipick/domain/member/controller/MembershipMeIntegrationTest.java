package com.recipick.domain.member.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipick.IntegrationTestSupport;
import com.recipick.domain.member.entity.Member;
import com.recipick.domain.member.entity.Membership;
import com.recipick.domain.member.entity.enums.MembershipSource;
import com.recipick.domain.member.entity.enums.MembershipType;
import com.recipick.domain.member.repository.MemberRepository;
import com.recipick.domain.member.repository.MembershipRepository;
import com.recipick.global.security.AuthPrincipal;
import com.recipick.global.security.JwtTokenProvider;
import com.recipick.global.security.SubjectType;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class MembershipMeIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private MemberRepository members;
    @Autowired
    private MembershipRepository memberships;
    @Autowired
    private JwtTokenProvider tokens;
    @Autowired
    private Clock clock;

    private Member member;
    private String token;
    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        member = createMember();
        token = tokens.createMemberTokens(member.getId()).accessToken();
        now = LocalDateTime.now(clock).withNano(0);
    }

    @Test
    @DisplayName("멤버십 이력이 없는 회원은 비활성·빈 현재 이용권·체험 가능으로 응답한다")
    void returnsEmptyMembershipForNewMember() throws Exception {
        // given
        assertThat(memberships.existsByMemberId(member.getId())).isFalse();

        // when
        MockHttpServletResponse response = request(token);

        // then
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(body(response).path("code").asText()).isEqualTo("OK");
        JsonNode data = body(response).path("data");
        assertThat(data.path("active").asBoolean()).isFalse();
        assertThat(data.path("current").isNull()).isTrue();
        assertThat(data.path("trialEligible").asBoolean()).isTrue();
    }

    @ParameterizedTest
    @CsvSource({"TRIAL,TRIAL,true", "PAID,SUBSCRIBE,true", "PAID,TRIAL_CONVERT,true", "PAID,ADMIN_GRANT,false"})
    @DisplayName("활성 이용권은 유형·발급 경로·기간·자동갱신·결제 기준일을 반환한다")
    void returnsCurrentMembership(String type, String source, boolean autoRenew) throws Exception {
        // given
        Membership membership = Membership.builder().member(member).type(MembershipType.valueOf(type))
                .source(MembershipSource.valueOf(source)).startAt(now.minusDays(1)).endAt(now.plusDays(6))
                .autoRenew(autoRenew)
                .billingAnchorDate(type.equals("PAID") && autoRenew ? now.minusDays(1).toLocalDate() : null).build();
        memberships.saveAndFlush(membership);

        // when
        MockHttpServletResponse response = request(token);

        // then
        assertThat(response.getStatus()).isEqualTo(200);
        JsonNode data = body(response).path("data");
        JsonNode current = data.path("current");
        assertThat(data.path("active").asBoolean()).isTrue();
        assertThat(data.path("trialEligible").asBoolean()).isFalse();
        assertThat(current.path("membershipId").asLong()).isEqualTo(membership.getId());
        assertThat(current.path("type").asText()).isEqualTo(type);
        assertThat(current.path("source").asText()).isEqualTo(source);
        assertThat(current.path("autoRenew").asBoolean()).isEqualTo(autoRenew);
        assertThat(current.path("startAt").asText()).isEqualTo(timestamp(now.minusDays(1)));
        assertThat(current.path("endAt").asText()).isEqualTo(timestamp(now.plusDays(6)));
        if (type.equals("PAID") && autoRenew) {
            assertThat(current.path("billingAnchorDate").asText()).isEqualTo(now.minusDays(1).toLocalDate().toString());
        } else {
            assertThat(current.path("billingAnchorDate").isNull()).isTrue();
        }
        assertThat(current.path("cancelReservedAt").isNull()).isTrue();
    }

    @Test
    @DisplayName("해지 예약된 이용권은 기간 안이면 활성 상태와 기존 해지 예약 정보를 유지한다")
    void keepsCancellationReservationActive() throws Exception {
        // given
        Membership membership = membership(now.minusDays(1), now.plusDays(29));
        membership.reserveCancellation(now.minusHours(1));
        memberships.saveAndFlush(membership);

        // when
        MockHttpServletResponse response = request(token);

        // then
        JsonNode data = body(response).path("data");
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(data.path("active").asBoolean()).isTrue();
        assertThat(data.path("current").path("autoRenew").asBoolean()).isFalse();
        assertThat(data.path("current").path("cancelReservedAt").asText())
                .isEqualTo(timestamp(now.minusHours(1)));
        assertThat(membership.getCancelReservedAt()).isEqualTo(now.minusHours(1));
    }

    @ParameterizedTest
    @CsvSource({"-7,-1", "1,7"})
    @DisplayName("만료되었거나 아직 시작되지 않은 이용권은 현재 이용권에서 제외한다")
    void excludesOutsidePeriod(int startDays, int endDays) throws Exception {
        // given
        memberships.saveAndFlush(membership(now.plusDays(startDays), now.plusDays(endDays)));

        // when
        MockHttpServletResponse response = request(token);

        // then
        assertThat(response.getStatus()).isEqualTo(200);
        JsonNode data = body(response).path("data");
        assertThat(data.path("active").asBoolean()).isFalse();
        assertThat(data.path("current").isNull()).isTrue();
        assertThat(data.path("trialEligible").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("즉시 종료된 이용권은 기간이 남아도 현재 이용권에서 제외한다")
    void excludesTerminatedMembership() throws Exception {
        // given
        Membership membership = membership(now.minusDays(1), now.plusDays(29));
        membership.terminateAfterRefund(now.minusHours(1));
        memberships.saveAndFlush(membership);

        // when
        MockHttpServletResponse response = request(token);

        // then
        JsonNode data = body(response).path("data");
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(data.path("active").asBoolean()).isFalse();
        assertThat(data.path("current").isNull()).isTrue();
        assertThat(data.path("trialEligible").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("만료된 관리자 지급 이력도 무료체험 자격을 제한한다")
    void countsExpiredAdminGrantAsHistory() throws Exception {
        // given
        memberships.saveAndFlush(Membership.builder().member(member).type(MembershipType.PAID)
                .source(MembershipSource.ADMIN_GRANT).startAt(now.minusDays(31)).endAt(now.minusDays(1)).build());

        // when
        MockHttpServletResponse response = request(token);

        // then
        JsonNode data = body(response).path("data");
        assertThat(data.path("current").isNull()).isTrue();
        assertThat(data.path("trialEligible").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("요청에 다른 회원 ID가 있어도 토큰 회원의 정보만 조회하며 내 정보와 체험 자격이 일치한다")
    void isolatesMemberAndMatchesProfileEligibility() throws Exception {
        // given
        Member other = createMember();
        memberships.saveAndFlush(Membership.builder().member(other).type(MembershipType.TRIAL)
                .source(MembershipSource.TRIAL).startAt(now.minusDays(1)).endAt(now.plusDays(6)).build());

        // when
        MockHttpServletResponse response = mockMvc.perform(get("/api/memberships/me")
                .param("memberId", other.getId().toString())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).andReturn().getResponse();
        MockHttpServletResponse profile = mockMvc.perform(get("/api/members/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).andReturn().getResponse();

        // then
        JsonNode data = body(response).path("data");
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(data.path("current").isNull()).isTrue();
        assertThat(data.path("trialEligible").asBoolean()).isTrue();
        assertThat(body(profile).path("data").path("trialEligible")).isEqualTo(data.path("trialEligible"));
    }

    @Test
    @DisplayName("비로그인·관리자·없는 회원은 멤버십을 조회할 수 없다")
    void enforcesMemberAuthentication() throws Exception {
        // given
        String missing = tokens.createMemberTokens(Long.MAX_VALUE).accessToken();
        String admin = tokens.createTokens(new AuthPrincipal(SubjectType.ADMIN, 1L, "SUPER_ADMIN", null)).accessToken();

        // when
        MockHttpServletResponse anonymous = request(null);
        MockHttpServletResponse administrator = request(admin);
        MockHttpServletResponse missingMember = request(missing);

        // then
        assertThat(anonymous.getStatus()).isEqualTo(401);
        assertThat(administrator.getStatus()).isEqualTo(403);
        assertThat(missingMember.getStatus()).isEqualTo(404);
        assertThat(body(missingMember).path("code").asText()).isEqualTo("MEMBER-001");
    }

    private String timestamp(LocalDateTime value) {
        return value.atOffset(ZoneOffset.ofHours(9)).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    private Member createMember() {
        return members.saveAndFlush(Member.builder().email(UUID.randomUUID() + "@mm.test")
                .phone(UUID.randomUUID().toString().substring(0, 20)).name("멤버십 조회 검증").passwordHash("hash").build());
    }

    private Membership membership(LocalDateTime start, LocalDateTime end) {
        return Membership.builder().member(member).type(MembershipType.PAID).source(MembershipSource.SUBSCRIBE)
                .startAt(start).endAt(end).autoRenew(true).billingAnchorDate(start.toLocalDate()).build();
    }

    private MockHttpServletResponse request(String access) throws Exception {
        var request = get("/api/memberships/me");
        if (access != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + access);
        }
        return mockMvc.perform(request).andReturn().getResponse();
    }

    private JsonNode body(MockHttpServletResponse response) throws Exception {
        return mapper.readTree(response.getContentAsString());
    }
}
