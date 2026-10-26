package com.recipick.domain.member.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipick.IntegrationTestSupport;
import com.recipick.domain.member.entity.Member;
import com.recipick.domain.member.entity.MemberAddress;
import com.recipick.domain.member.repository.MemberRepository;
import com.recipick.global.security.AuthPrincipal;
import com.recipick.global.security.JwtTokenProvider;
import com.recipick.global.security.SubjectType;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
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
class MemberAddressListIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private MemberRepository members;
    @Autowired
    private JwtTokenProvider tokens;
    @Autowired
    private JdbcTemplate jdbc;

    private Member member;

    @BeforeEach
    void setUp() {
        member = createMember();
    }

    @Test
    @DisplayName("저장된 배송지가 없으면 최대 개수와 빈 목록을 정상 반환한다")
    void returnsEmptyList() throws Exception {
        // given
        String token = token(member);

        // when
        MockHttpServletResponse response = request(token);

        // then
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(data(response).path("maxCount").asInt()).isEqualTo(5);
        assertThat(data(response).path("addresses").isArray()).isTrue();
        assertThat(data(response).path("addresses").isEmpty()).isTrue();
    }

    @Test
    @DisplayName("본인 현재 배송지를 먼저 반환하고 나머지는 최근 등록순으로 정렬한다")
    void sortsOwnedAddresses() throws Exception {
        // given
        MemberAddress selected = address(member, "현재", "1111014000");
        MemberAddress older = address(member, "이전", "4121010300");
        MemberAddress newer = address(member, "최근", "9999999999");
        Member other = createMember();
        address(other, "다른 회원", "1111014000");
        member.selectAddress(selected, 1L);
        entityManager.flush();
        jdbc.update("UPDATE member_address SET created_at = '2020-01-01 00:00:00' WHERE id = ?", selected.getId());
        jdbc.update("UPDATE member_address SET created_at = '2021-01-01 00:00:00' WHERE id = ?", older.getId());
        jdbc.update("UPDATE member_address SET created_at = '2022-01-01 00:00:00' WHERE id = ?", newer.getId());

        // when
        MockHttpServletResponse response = request(token(member));

        // then
        assertThat(response.getStatus()).isEqualTo(200);
        JsonNode addresses = data(response).path("addresses");
        assertThat(addresses.size()).isEqualTo(3);
        assertThat(addresses.get(0).path("addressId").asLong()).isEqualTo(selected.getId());
        assertThat(addresses.get(0).path("selected").asBoolean()).isTrue();
        assertThat(addresses.get(1).path("addressId").asLong()).isEqualTo(newer.getId());
        assertThat(addresses.get(2).path("addressId").asLong()).isEqualTo(older.getId());
        assertThat(addresses.get(1).path("selected").asBoolean()).isFalse();
        assertThat(addresses.get(2).path("selected").asBoolean()).isFalse();
        assertThat(addresses.get(0).path("serviceAvailable").asBoolean()).isTrue();
        assertThat(addresses.get(1).path("serviceAvailable").asBoolean()).isFalse();
        assertThat(addresses.get(2).path("serviceAvailable").asBoolean()).isTrue();
        assertThat(addresses.get(0).size()).isEqualTo(7);
    }

    @Test
    @DisplayName("등록 시각이 같으면 ID가 큰 배송지부터 반환한다")
    void breaksTimestampTiesById() throws Exception {
        // given
        MemberAddress first = address(member, null, "1111014000");
        MemberAddress second = address(member, "다음", "1111014000");
        jdbc.update("UPDATE member_address SET created_at = '2020-01-01 00:00:00' WHERE member_id = ?", member.getId());

        // when
        MockHttpServletResponse response = request(token(member));

        // then
        JsonNode addresses = data(response).path("addresses");
        assertThat(addresses.get(0).path("addressId").asLong()).isEqualTo(second.getId());
        assertThat(addresses.get(1).path("addressId").asLong()).isEqualTo(first.getId());
        assertThat(addresses.get(1).path("label").isNull()).isTrue();
    }

    @Test
    @DisplayName("운영 상태는 매번 다시 확인하고 회원·배송지 정보를 변경하지 않는다")
    void recalculatesAvailabilityWithoutWrites() throws Exception {
        // given
        MemberAddress selected = address(member, "집", "1111014000");
        member.selectAddress(selected, 1L);
        entityManager.flush();
        var memberBefore = jdbc.queryForMap("SELECT * FROM member WHERE id = ?", member.getId());
        var addressesBefore = jdbc.queryForList("SELECT * FROM member_address WHERE member_id = ?", member.getId());
        String token = token(member);

        // when
        MockHttpServletResponse open = request(token);
        jdbc.update("UPDATE mart SET status = 'CLOSED' WHERE id = 1");
        MockHttpServletResponse closed = request(token);
        jdbc.update("UPDATE mart SET status = 'OPEN' WHERE id = 1");
        MockHttpServletResponse reopened = request(token);

        // then
        assertThat(data(open).path("addresses").get(0).path("serviceAvailable").asBoolean()).isTrue();
        assertThat(data(closed).path("addresses").get(0).path("serviceAvailable").asBoolean()).isFalse();
        assertThat(data(reopened).path("addresses").get(0).path("serviceAvailable").asBoolean()).isTrue();
        assertThat(jdbc.queryForMap("SELECT * FROM member WHERE id = ?", member.getId())).isEqualTo(memberBefore);
        assertThat(jdbc.queryForList("SELECT * FROM member_address WHERE member_id = ?", member.getId()))
                .isEqualTo(addressesBefore);
    }

    @Test
    @DisplayName("요청에 다른 회원 ID를 넣어도 토큰의 본인 배송지만 반환한다")
    void ignoresSuppliedMemberId() throws Exception {
        // given
        address(member, "본인", "1111014000");
        Member other = createMember();
        address(other, "다른 회원", "1111014000");

        // when
        MockHttpServletResponse response = mockMvc.perform(get("/api/members/me/addresses")
                .param("memberId", other.getId().toString())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(member))).andReturn().getResponse();

        // then
        assertThat(data(response).path("addresses").size()).isEqualTo(1);
        assertThat(data(response).path("addresses").get(0).path("label").asText()).isEqualTo("본인");
    }

    @Test
    @DisplayName("존재하지 않는 회원 토큰이면 빈 목록 대신 AUTH-011을 반환한다")
    void rejectsMissingMember() throws Exception {
        // given
        String token = tokens.createMemberTokens(Long.MAX_VALUE).accessToken();

        // when
        MockHttpServletResponse response = request(token);

        // then
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(mapper.readTree(response.getContentAsString()).path("code").asText()).isEqualTo("AUTH-011");
    }

    @Test
    @DisplayName("비로그인 요청은 401이고 관리자 토큰은 403이다")
    void enforcesMemberAuthentication() throws Exception {
        // given
        String adminToken = tokens.createTokens(new AuthPrincipal(SubjectType.ADMIN, 1L, "SUPER_ADMIN", null))
                .accessToken();

        // when
        MockHttpServletResponse anonymous = mockMvc.perform(get("/api/members/me/addresses"))
                .andReturn().getResponse();
        MockHttpServletResponse admin = request(adminToken);

        // then
        assertThat(anonymous.getStatus()).isEqualTo(401);
        assertThat(admin.getStatus()).isEqualTo(403);
    }

    private Member createMember() {
        return members.saveAndFlush(Member.builder().email(UUID.randomUUID() + "@addr.test").name("배송지 검증")
                .phone(UUID.randomUUID().toString().substring(0, 20)).passwordHash("test-password-hash").build());
    }

    private MemberAddress address(Member owner, String label, String code) {
        MemberAddress address = MemberAddress.builder().member(owner).label(label).address("테스트 주소")
                .detailAddress("101호").legalDongCode(code).build();
        entityManager.persist(address);
        entityManager.flush();
        return address;
    }

    private String token(Member owner) {
        return tokens.createMemberTokens(owner.getId()).accessToken();
    }

    private MockHttpServletResponse request(String token) throws Exception {
        return mockMvc.perform(get("/api/members/me/addresses").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andReturn().getResponse();
    }

    private JsonNode data(MockHttpServletResponse response) throws Exception {
        return mapper.readTree(response.getContentAsString(StandardCharsets.UTF_8)).path("data");
    }
}
