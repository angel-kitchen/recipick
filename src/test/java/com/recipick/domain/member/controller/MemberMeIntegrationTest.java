package com.recipick.domain.member.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipick.IntegrationTestSupport;
import com.recipick.domain.member.dto.MemberProfile;
import com.recipick.domain.member.entity.Member;
import com.recipick.domain.member.entity.MemberAddress;
import com.recipick.domain.member.entity.Membership;
import com.recipick.domain.member.entity.enums.MembershipSource;
import com.recipick.domain.member.entity.enums.MembershipType;
import com.recipick.domain.member.repository.MemberRepository;
import com.recipick.domain.member.repository.MembershipRepository;
import com.recipick.domain.member.service.MemberService;
import com.recipick.global.security.AuthPrincipal;
import com.recipick.global.security.JwtTokenProvider;
import com.recipick.global.security.SubjectType;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class MemberMeIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private MemberRepository members;
    @Autowired
    private MembershipRepository memberships;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JwtTokenProvider tokens;
    @Autowired
    private Clock clock;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private MemberService memberService;

    private Member member;

    @BeforeEach
    void setUp() {
        member = members.saveAndFlush(Member.builder().email(UUID.randomUUID() + "@me.test").name("조회 회원")
                .phone("010" + String.format("%08d", ThreadLocalRandom.current().nextInt(100000000)))
                .passwordHash("test-password-hash").build());
    }

    @Test
    @DisplayName("가입 직후에는 본인 정보와 빈 배송지·마트·멤버십을 반환한다")
    void returnsNewMemberProfile() throws Exception {
        // given
        String token = tokens.createMemberTokens(member.getId()).accessToken();

        // when
        MockHttpServletResponse response = request(token);

        // then
        assertThat(response.getStatus()).isEqualTo(200);
        JsonNode data = data(response);
        assertThat(data.path("memberId").asLong()).isEqualTo(member.getId());
        assertThat(data.path("email").asText()).isEqualTo(member.getEmail());
        assertThat(data.path("name").asText()).isEqualTo(member.getName());
        assertThat(data.path("phone").asText()).isEqualTo(member.getPhone());
        assertThat(data.path("locationAgreed").asBoolean()).isFalse();
        assertThat(data.path("mart").isNull()).isTrue();
        assertThat(data.path("currentAddress").isNull()).isTrue();
        assertThat(data.path("membership").path("active").asBoolean()).isFalse();
        assertThat(data.path("membership").path("type").isNull()).isTrue();
        assertThat(data.path("membership").path("endAt").isNull()).isTrue();
        assertThat(data.path("trialEligible").asBoolean()).isTrue();
        assertThat(data.has("passwordHash")).isFalse();
        assertThat(response.getHeader("Set-Cookie")).isNull();
    }

    @Test
    @DisplayName("현재 배송지·이용 마트·동의 여부와 활성 멤버십을 함께 조회한다")
    void returnsSelectedAddressAndMembership() throws Exception {
        // given
        selectAddress("1111014000", 1L);
        LocalDateTime now = LocalDateTime.now(clock);
        member.agreeToLocation(now);
        memberships.saveAndFlush(Membership.builder().member(member).type(MembershipType.PAID)
                .source(MembershipSource.ADMIN_GRANT).startAt(now.minusDays(1)).endAt(now.plusDays(29)).build());

        // when
        MockHttpServletResponse response = request(tokens.createMemberTokens(member.getId()).accessToken());

        // then
        assertThat(response.getStatus()).isEqualTo(200);
        JsonNode data = data(response);
        assertThat(data.path("locationAgreed").asBoolean()).isTrue();
        assertThat(data.path("mart").path("martId").asLong()).isEqualTo(1L);
        assertThat(data.path("mart").path("name").asText()).isEqualTo("삼청 마트");
        assertThat(data.path("currentAddress").path("label").asText()).isEqualTo("집");
        assertThat(data.path("currentAddress").path("detailAddress").asText()).isEqualTo("101호");
        assertThat(data.path("currentAddress").path("legalDongCode").asText()).isEqualTo("1111014000");
        assertThat(data.path("currentAddress").path("serviceAvailable").asBoolean()).isTrue();
        assertThat(data.path("membership").path("active").asBoolean()).isTrue();
        assertThat(data.path("membership").path("type").asText()).isEqualTo("PAID");
        assertThat(data.path("membership").path("endAt").asText()).endsWith("+09:00");
        assertThat(data.path("trialEligible").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("서비스 지역 밖 배송지도 보여 주되 이용 마트는 없다")
    void returnsUnsupportedAddress() throws Exception {
        // given
        selectAddress("9999999999", null);

        // when
        MockHttpServletResponse response = request(tokens.createMemberTokens(member.getId()).accessToken());

        // then
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(data(response).path("currentAddress").path("serviceAvailable").asBoolean()).isFalse();
        assertThat(data(response).path("mart").isNull()).isTrue();
    }

    @Test
    @DisplayName("마트 운영 중지 시 이용 불가를 표시하며 저장된 이용 마트를 변경하지 않는다")
    void reflectsClosedMartWithoutChangingMember() throws Exception {
        // given
        selectAddress("1111014000", 1L);
        jdbc.update("UPDATE mart SET status = 'CLOSED' WHERE id = 1");
        entityManager.flush();
        var before = jdbc.queryForMap("SELECT * FROM member WHERE id = ?", member.getId());

        // when
        MockHttpServletResponse response = request(tokens.createMemberTokens(member.getId()).accessToken());

        // then
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(data(response).path("currentAddress").path("serviceAvailable").asBoolean()).isFalse();
        assertThat(data(response).path("mart").path("martId").asLong()).isEqualTo(1L);
        assertThat(data(response).path("mart").path("name").asText()).isEqualTo("삼청 마트");
        assertThat(jdbc.queryForMap("SELECT * FROM member WHERE id = ?", member.getId())).isEqualTo(before);
    }

    @Test
    @DisplayName("배송지와 지정 마트가 다르면 지정 마트를 보여 주고 이용 가능으로 표시하지 않는다")
    void rejectsMismatchedMartAvailability() throws Exception {
        // given
        selectAddress("1111014000", 2L);

        // when
        MockHttpServletResponse response = request(tokens.createMemberTokens(member.getId()).accessToken());

        // then
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(data(response).path("mart").path("martId").asLong()).isEqualTo(2L);
        assertThat(data(response).path("mart").path("name").asText()).isEqualTo("하안 마트");
        assertThat(data(response).path("currentAddress").path("serviceAvailable").asBoolean()).isFalse();
        assertThat(jdbc.queryForObject("SELECT mart_id FROM member WHERE id = ?", Long.class, member.getId()))
                .isEqualTo(2L);
    }

    @Test
    @DisplayName("지정 마트가 없으면 서비스 지역 주소라도 이용 가능으로 표시하지 않는다")
    void requiresAssignedMartForAvailability() throws Exception {
        // given
        selectAddress("1111014000", null);

        // when
        MockHttpServletResponse response = request(tokens.createMemberTokens(member.getId()).accessToken());

        // then
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(data(response).path("mart").isNull()).isTrue();
        assertThat(data(response).path("currentAddress").path("serviceAvailable").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("존재하지 않는 지정 마트는 정보 없음으로 응답하고 저장값을 변경하지 않는다")
    void handlesMissingAssignedMart() throws Exception {
        // given
        selectAddress("1111014000", Long.MAX_VALUE);

        // when
        MockHttpServletResponse response = request(tokens.createMemberTokens(member.getId()).accessToken());

        // then
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(data(response).path("mart").isNull()).isTrue();
        assertThat(data(response).path("currentAddress").path("serviceAvailable").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("요청 파라미터로 다른 회원 ID를 보내도 토큰의 본인 정보만 조회한다")
    void ignoresSuppliedMemberId() throws Exception {
        // given
        String token = tokens.createMemberTokens(member.getId()).accessToken();

        // when
        MockHttpServletResponse response = mockMvc.perform(get("/api/members/me")
                .param("memberId", Long.toString(Long.MAX_VALUE))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).andReturn().getResponse();

        // then
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(data(response).path("memberId").asLong()).isEqualTo(member.getId());
    }

    @Test
    @DisplayName("유효한 회원 토큰이어도 회원이 없으면 MEMBER-001을 반환한다")
    void rejectsMissingMember() throws Exception {
        // given
        String token = tokens.createMemberTokens(Long.MAX_VALUE).accessToken();

        // when
        MockHttpServletResponse response = request(token);

        // then
        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(mapper.readTree(response.getContentAsString()).path("code").asText()).isEqualTo("MEMBER-001");
    }

    @Test
    @DisplayName("비로그인 요청은 401이고 관리자 토큰은 403이다")
    void enforcesMemberAuthentication() throws Exception {
        // given
        String adminToken = tokens.createTokens(new AuthPrincipal(SubjectType.ADMIN, 1L, "SUPER_ADMIN", null))
                .accessToken();

        // when
        MockHttpServletResponse anonymous = mockMvc.perform(get("/api/members/me")).andReturn().getResponse();
        MockHttpServletResponse admin = request(adminToken);

        // then
        assertThat(anonymous.getStatus()).isEqualTo(401);
        assertThat(admin.getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("개인정보 DTO를 문자열로 출력해도 전화번호와 주소가 노출되지 않는다")
    void redactsSensitiveDtoStrings() {
        // given
        selectAddress("1111014000", 1L);

        // when
        MemberProfile profile = memberService.getProfile(member.getId());

        // then
        assertThat(profile.toString()).doesNotContain(member.getPhone(), "101호");
        assertThat(profile.address().toString()).doesNotContain("101호");
    }

    private void selectAddress(String code, Long martId) {
        MemberAddress address = MemberAddress.builder().member(member).label("집")
                .address("테스트 주소").detailAddress("101호").legalDongCode(code).build();
        entityManager.persist(address);
        member.selectAddress(address, martId);
        entityManager.flush();
    }

    private MockHttpServletResponse request(String token) throws Exception {
        return mockMvc.perform(get("/api/members/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andReturn().getResponse();
    }

    private JsonNode data(MockHttpServletResponse response) throws Exception {
        return mapper.readTree(response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).path("data");
    }
}
