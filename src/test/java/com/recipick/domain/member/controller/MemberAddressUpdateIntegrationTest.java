package com.recipick.domain.member.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

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
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class MemberAddressUpdateIntegrationTest extends IntegrationTestSupport {

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
    private MemberAddress address;

    @BeforeEach
    void setUp() {
        member = members.saveAndFlush(Member.builder().email(UUID.randomUUID() + "@ac.test")
                .phone(UUID.randomUUID().toString().substring(0, 20)).name("배송지 등록 검증")
                .passwordHash("test-password-hash").build());
        token = tokens.createMemberTokens(member.getId()).accessToken();
        address = addresses.saveAndFlush(MemberAddress.builder().member(member).label("집")
                .address("기존 주소").detailAddress("101호").legalDongCode("1111014000").build());
        member.selectAddress(address, 1L);
        members.flush();
    }

    @Test
    @DisplayName("명칭만 수정하면 주소는 유지하고 명칭 null은 삭제로 처리한다")
    void updatesAndClearsLabel() throws Exception {
        // given
        Map<String, Object> clear = new HashMap<>();
        clear.put("label", null);

        // when
        MockHttpServletResponse renamed = request(Map.of("label", "회사"), token);
        MockHttpServletResponse cleared = request(clear, token);

        // then
        assertThat(renamed.getStatus()).isEqualTo(200);
        assertThat(data(renamed).path("label").asText()).isEqualTo("회사");
        assertThat(data(renamed).path("address").asText()).isEqualTo("기존 주소");
        assertThat(cleared.getStatus()).isEqualTo(200);
        assertThat(data(cleared).path("label").isNull()).isTrue();
        assertThat(address.getLabel()).isNull();
        assertThat(address.getAddress()).isEqualTo("기존 주소");
    }

    @Test
    @DisplayName("현재 배송지의 주소를 수정하면 이용 마트도 변경하고 생략한 명칭은 유지한다")
    void changesSelectedAddressAndMart() throws Exception {
        // given
        Map<String, Object> body = body("4121010300");

        // when
        MockHttpServletResponse response = request(body, token);

        // then
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(data(response).path("label").asText()).isEqualTo("집");
        assertThat(data(response).path("selected").asBoolean()).isTrue();
        assertThat(data(response).path("martChanged").asBoolean()).isTrue();
        assertThat(data(response).path("mart").path("martId").asLong()).isEqualTo(2L);
        assertThat(member.getMartId()).isEqualTo(2L);
        assertThat(member.getSelectedAddress().getId()).isEqualTo(address.getId());
        assertThat(address.getLegalDongCode()).isEqualTo("4121010300");
    }

    @Test
    @DisplayName("현재 배송지를 서비스 밖 주소로 바꾸면 이용 마트를 해제한다")
    void allowsUnsupportedAddress() throws Exception {
        // given
        Map<String, Object> body = body("9999999999");

        // when
        MockHttpServletResponse response = request(body, token);

        // then
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(data(response).path("serviceAvailable").asBoolean()).isFalse();
        assertThat(data(response).path("mart").isNull()).isTrue();
        assertThat(data(response).path("martChanged").asBoolean()).isTrue();
        assertThat(member.getMartId()).isNull();
    }

    @Test
    @DisplayName("다른 배송지를 수정해도 현재 배송지와 이용 마트는 유지한다")
    void preservesSelectionForOtherAddress() throws Exception {
        // given
        MemberAddress current = address;
        address = addresses.saveAndFlush(MemberAddress.builder().member(member).address("추가 주소")
                .detailAddress("2호").legalDongCode("1111014000").build());

        // when
        MockHttpServletResponse response = request(body("4121010300"), token);

        // then
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(data(response).path("selected").asBoolean()).isFalse();
        assertThat(data(response).path("serviceAvailable").asBoolean()).isTrue();
        assertThat(data(response).path("martChanged").asBoolean()).isFalse();
        assertThat(data(response).path("mart").path("martId").asLong()).isEqualTo(1L);
        assertThat(member.getSelectedAddress().getId()).isEqualTo(current.getId());
        assertThat(member.getMartId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("현재 배송지 수정은 마트 운영 중지와 재개를 반영한다")
    void reflectsMartStatusChanges() throws Exception {
        // given
        jdbc.update("UPDATE mart SET status = 'CLOSED' WHERE id = 1");

        // when
        MockHttpServletResponse closed = request(Map.of("label", "집"), token);
        jdbc.update("UPDATE mart SET status = 'OPEN' WHERE id = 1");
        MockHttpServletResponse reopened = request(Map.of("label", "집"), token);

        // then
        assertThat(closed.getStatus()).isEqualTo(200);
        assertThat(data(closed).path("serviceAvailable").asBoolean()).isFalse();
        assertThat(data(closed).path("mart").isNull()).isTrue();
        assertThat(reopened.getStatus()).isEqualTo(200);
        assertThat(data(reopened).path("mart").path("martId").asLong()).isEqualTo(1L);
        assertThat(member.getMartId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("다른 회원 배송지와 없는 배송지는 동일한 ADDRESS-001로 거절한다")
    void hidesUnownedAddresses() throws Exception {
        // given
        String other = tokens.createMemberTokens(Long.MAX_VALUE).accessToken();
        Member owner = members.saveAndFlush(Member.builder().email(UUID.randomUUID() + "@owner.test")
                .phone(UUID.randomUUID().toString().substring(0, 20)).passwordHash("hash").build());
        String ownerToken = tokens.createMemberTokens(owner.getId()).accessToken();

        // when
        MockHttpServletResponse unowned = request(Map.of("label", "수정 시도"), ownerToken);
        MockHttpServletResponse missingMember = request(Map.of("label", "수정 시도"), other);
        MockHttpServletResponse missing = mockMvc.perform(patch("/api/members/me/addresses/{id}", Long.MAX_VALUE)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"label\":\"수정 시도\"}")).andReturn().getResponse();

        // then
        assertThat(unowned.getStatus()).isEqualTo(404);
        assertThat(mapper.readTree(unowned.getContentAsString()).path("code").asText()).isEqualTo("ADDRESS-001");
        assertThat(missing.getStatus()).isEqualTo(404);
        assertThat(mapper.readTree(missing.getContentAsString()).path("code").asText()).isEqualTo("ADDRESS-001");
        assertThat(missingMember.getStatus()).isEqualTo(404);
        assertThat(address.getLabel()).isEqualTo("집");
    }

    @Test
    @DisplayName("빈 요청과 주소 묶음 일부만 보낸 요청은 기존 값을 변경하지 않는다")
    void rejectsIncompleteUpdates() throws Exception {
        // given
        Map<String, Object> onlyAddress = Map.of("label", "변경 금지", "address", "새 주소");

        // when
        MockHttpServletResponse empty = request(Map.of(), token);
        MockHttpServletResponse incomplete = request(onlyAddress, token);

        // then
        assertThat(empty.getStatus()).isEqualTo(400);
        assertThat(incomplete.getStatus()).isEqualTo(400);
        assertThat(address.getLabel()).isEqualTo("집");
        assertThat(address.getAddress()).isEqualTo("기존 주소");
        assertThat(member.getMartId()).isEqualTo(1L);
    }

    @ParameterizedTest
    @MethodSource("invalidFields")
    @DisplayName("잘못된 수정 값은 COMMON-001로 거절하고 기존 주소를 유지한다")
    void validatesInput(String field, Object value) throws Exception {
        // given
        Map<String, Object> body = body("4121010300");
        body.put(field, value);

        // when
        MockHttpServletResponse response = request(body, token);

        // then
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(mapper.readTree(response.getContentAsString()).path("code").asText()).isEqualTo("COMMON-001");
        assertThat(address.getLegalDongCode()).isEqualTo("1111014000");
        assertThat(member.getMartId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("회원 토큰 없이 또는 관리자 토큰으로 배송지를 수정할 수 없다")
    void enforcesMemberAuthentication() throws Exception {
        // given
        String admin = tokens.createTokens(new AuthPrincipal(SubjectType.ADMIN, 1L, "SUPER_ADMIN", null)).accessToken();

        // when
        MockHttpServletResponse anonymous = request(Map.of("label", "회사"), null);
        MockHttpServletResponse administrator = request(Map.of("label", "회사"), admin);

        // then
        assertThat(anonymous.getStatus()).isEqualTo(401);
        assertThat(administrator.getStatus()).isEqualTo(403);
        assertThat(address.getLabel()).isEqualTo("집");
    }

    private static Stream<Arguments> invalidFields() {
        return Stream.of(Arguments.of("label", "a".repeat(31)), Arguments.of("address", null),
                Arguments.of("address", " "), Arguments.of("address", "a".repeat(256)),
                Arguments.of("detailAddress", null), Arguments.of("detailAddress", " "),
                Arguments.of("detailAddress", "a".repeat(256)), Arguments.of("legalDongCode", null),
                Arguments.of("legalDongCode", "111101400"), Arguments.of("legalDongCode", "abcdefghij"),
                Arguments.of("legalDongCode", "１１１１０１４０００"));
    }

    private Map<String, Object> body(String code) {
        Map<String, Object> body = new HashMap<>();
        body.put("address", "테스트 주소");
        body.put("detailAddress", "101호");
        body.put("legalDongCode", code);
        return body;
    }

    private MockHttpServletResponse request(Map<String, Object> body, String access) throws Exception {
        var request = patch("/api/members/me/addresses/{id}", address.getId()).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body));
        if (access != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + access);
        }
        return mockMvc.perform(request).andReturn().getResponse();
    }

    private JsonNode data(MockHttpServletResponse response) throws Exception {
        return mapper.readTree(response.getContentAsString(StandardCharsets.UTF_8)).path("data");
    }
}
