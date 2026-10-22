package com.recipick.domain.member.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipick.IntegrationTestSupport;
import com.recipick.domain.member.entity.Member;
import com.recipick.domain.member.entity.MemberAddress;
import com.recipick.domain.member.repository.MemberAddressRepository;
import com.recipick.domain.member.repository.MemberRepository;
import com.recipick.global.security.AuthPrincipal;
import com.recipick.global.security.JwtTokenProvider;
import com.recipick.global.security.SubjectType;
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
class MemberAddressSelectIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private MemberRepository members;
    @Autowired
    private MemberAddressRepository addresses;
    @Autowired
    private JwtTokenProvider tokens;
    @Autowired
    private JdbcTemplate jdbc;

    private Member member;
    private String token;

    @BeforeEach
    void setUp() {
        member = members.saveAndFlush(Member.builder().email(UUID.randomUUID() + "@as.test")
                .phone(UUID.randomUUID().toString().substring(0, 20)).name("배송지 선택 검증")
                .passwordHash("test-password-hash").build());
        token = tokens.createMemberTokens(member.getId()).accessToken();
    }

    @Test
    @DisplayName("현재 배송지를 재선택하면 운영 재개된 마트를 연결하고 반복 요청은 변경하지 않는다")
    void restoresMartOnReselect() throws Exception {
        // given
        MemberAddress address = address(member, "1111014000");
        jdbc.update("UPDATE mart SET status = 'CLOSED' WHERE id = 1");
        member.selectAddress(address, null);
        members.flush();
        jdbc.update("UPDATE mart SET status = 'OPEN' WHERE id = 1");

        // when
        MockHttpServletResponse response = select(address.getId(), token);
        MockHttpServletResponse repeated = select(address.getId(), token);

        // then
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(data(response).path("mart").path("martId").asLong()).isEqualTo(1L);
        assertThat(data(response).path("serviceAvailable").asBoolean()).isTrue();
        assertThat(data(response).path("martChanged").asBoolean()).isTrue();
        assertThat(data(repeated).path("martChanged").asBoolean()).isFalse();
        assertThat(member.getMartId()).isEqualTo(1L);
        assertThat(member.getSelectedAddress().getId()).isEqualTo(address.getId());
    }

    @Test
    @DisplayName("재선택 시 마트가 운영 중지이면 이용 마트를 비운다")
    void clearsClosedMartOnReselect() throws Exception {
        // given
        MemberAddress address = address(member, "1111014000");
        member.selectAddress(address, 1L);
        jdbc.update("UPDATE mart SET status = 'CLOSED' WHERE id = 1");

        // when
        MockHttpServletResponse response = select(address.getId(), token);

        // then
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(data(response).path("serviceAvailable").asBoolean()).isFalse();
        assertThat(data(response).path("mart").isNull()).isTrue();
        assertThat(data(response).path("martChanged").asBoolean()).isTrue();
        assertThat(member.getMartId()).isNull();
    }

    @Test
    @DisplayName("다른 마트 지역과 서비스 밖 배송지도 선택하며 이용 마트를 다시 판정한다")
    void selectsOtherRegionAndUnsupportedAddress() throws Exception {
        // given
        MemberAddress first = address(member, "1111014000");
        MemberAddress other = address(member, "4121010300");
        MemberAddress unsupported = address(member, "9999999999");
        member.selectAddress(first, 1L);

        // when
        MockHttpServletResponse changed = select(other.getId(), token);
        MockHttpServletResponse unavailable = select(unsupported.getId(), token);

        // then
        assertThat(changed.getStatus()).isEqualTo(200);
        assertThat(data(changed).path("mart").path("martId").asLong()).isEqualTo(2L);
        assertThat(data(changed).path("martChanged").asBoolean()).isTrue();
        assertThat(unavailable.getStatus()).isEqualTo(200);
        assertThat(data(unavailable).path("mart").isNull()).isTrue();
        assertThat(member.getSelectedAddress().getId()).isEqualTo(unsupported.getId());
        assertThat(member.getLocationAgreedAt()).isNull();
        assertThat(addresses.countByMemberId(member.getId())).isEqualTo(3);
    }

    @Test
    @DisplayName("다른 회원 배송지와 없는 배송지는 같은 404이며 기존 선택은 유지한다")
    void hidesUnownedAddresses() throws Exception {
        // given
        MemberAddress own = address(member, "1111014000");
        member.selectAddress(own, 1L);
        Member other = members.saveAndFlush(Member.builder().email(UUID.randomUUID() + "@other.test")
                .phone(UUID.randomUUID().toString().substring(0, 20)).passwordHash("hash").build());
        MemberAddress foreign = address(other, "4121010300");

        // when
        MockHttpServletResponse unowned = select(foreign.getId(), token);
        MockHttpServletResponse missing = select(Long.MAX_VALUE, token);

        // then
        assertThat(unowned.getStatus()).isEqualTo(404);
        assertThat(missing.getStatus()).isEqualTo(404);
        assertThat(mapper.readTree(unowned.getContentAsString()).path("code").asText()).isEqualTo("ADDRESS-001");
        assertThat(mapper.readTree(missing.getContentAsString()).path("code").asText()).isEqualTo("ADDRESS-001");
        assertThat(member.getSelectedAddress().getId()).isEqualTo(own.getId());
        assertThat(member.getMartId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("비로그인과 관리자 토큰으로 회원의 배송지를 선택할 수 없다")
    void rejectsNonMemberTokens() throws Exception {
        // given
        MemberAddress address = address(member, "1111014000");
        String admin = tokens.createTokens(new AuthPrincipal(SubjectType.ADMIN, 1L, "SUPER_ADMIN", null)).accessToken();

        // when
        MockHttpServletResponse anonymous = select(address.getId(), null);
        MockHttpServletResponse administrator = select(address.getId(), admin);

        // then
        assertThat(anonymous.getStatus()).isEqualTo(401);
        assertThat(administrator.getStatus()).isEqualTo(403);
        assertThat(member.getSelectedAddress()).isNull();
    }

    private MemberAddress address(Member owner, String code) {
        return addresses.saveAndFlush(MemberAddress.builder().member(owner).address("테스트 주소")
                .detailAddress("101호").legalDongCode(code).build());
    }

    private MockHttpServletResponse select(Long addressId, String access) throws Exception {
        var request = put("/api/members/me/addresses/{addressId}/select", addressId);
        if (access != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + access);
        }
        return mockMvc.perform(request).andReturn().getResponse();
    }

    private JsonNode data(MockHttpServletResponse response) throws Exception {
        return mapper.readTree(response.getContentAsString()).path("data");
    }
}
