package com.recipick.domain.member.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

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
import java.time.LocalDateTime;
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
class MemberAddressCreateIntegrationTest extends IntegrationTestSupport {

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
        member = members.saveAndFlush(Member.builder().email(UUID.randomUUID() + "@ac.test")
                .phone(UUID.randomUUID().toString().substring(0, 20)).name("배송지 등록 검증")
                .passwordHash("test-password-hash").build());
        token = tokens.createMemberTokens(member.getId()).accessToken();
    }

    @Test
    @DisplayName("첫 배송지는 선택 옵션 생략 시에도 자동 선택하고 마트를 지정한다")
    void automaticallySelectsFirstAddress() throws Exception {
        // given
        Map<String, Object> body = body("1111014000");

        // when
        MockHttpServletResponse response = request(body, token);

        // then
        assertThat(response.getStatus()).isEqualTo(201);
        JsonNode data = data(response);
        assertThat(data.path("selected").asBoolean()).isTrue();
        assertThat(data.path("serviceAvailable").asBoolean()).isTrue();
        assertThat(data.path("martChanged").asBoolean()).isTrue();
        assertThat(data.path("mart").path("martId").asLong()).isEqualTo(1L);
        assertThat(member.getSelectedAddress().getId()).isEqualTo(data.path("addressId").asLong());
        assertThat(member.getMartId()).isEqualTo(1L);
        assertThat(member.getLocationAgreedAt()).isNull();
        assertThat(addresses.countByMemberId(member.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("선택하지 않은 추가 주소는 현재 배송지와 마트를 유지한다")
    void preservesSelectionWhenNotRequested() throws Exception {
        // given
        request(body("1111014000"), token);
        Long selectedId = member.getSelectedAddress().getId();
        Map<String, Object> body = body("4121010300");
        body.put("select", false);

        // when
        MockHttpServletResponse response = request(body, token);

        // then
        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(data(response).path("selected").asBoolean()).isFalse();
        assertThat(data(response).path("serviceAvailable").asBoolean()).isTrue();
        assertThat(data(response).path("martChanged").asBoolean()).isFalse();
        assertThat(data(response).path("mart").path("martId").asLong()).isEqualTo(1L);
        assertThat(member.getSelectedAddress().getId()).isEqualTo(selectedId);
        assertThat(member.getMartId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("서비스 밖 주소를 선택하지 않고 추가하면 기존 이용 마트는 유지된다")
    void savesUnsupportedAddressWithoutChangingSelection() throws Exception {
        // given
        request(body("1111014000"), token);
        Long selectedId = member.getSelectedAddress().getId();

        // when
        MockHttpServletResponse response = request(body("9999999999"), token);

        // then
        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(data(response).path("selected").asBoolean()).isFalse();
        assertThat(data(response).path("serviceAvailable").asBoolean()).isFalse();
        assertThat(data(response).path("mart").path("martId").asLong()).isEqualTo(1L);
        assertThat(data(response).path("martChanged").asBoolean()).isFalse();
        assertThat(member.getSelectedAddress().getId()).isEqualTo(selectedId);
    }

    @Test
    @DisplayName("선택 옵션이 true이면 등록한 주소로 선택과 이용 마트를 갱신한다")
    void selectsNewAddressAndChangesMart() throws Exception {
        // given
        request(body("1111014000"), token);
        Map<String, Object> body = body("4121010300");
        body.put("select", true);

        // when
        MockHttpServletResponse response = request(body, token);

        // then
        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(data(response).path("selected").asBoolean()).isTrue();
        assertThat(data(response).path("martChanged").asBoolean()).isTrue();
        assertThat(member.getMartId()).isEqualTo(2L);
        assertThat(member.getSelectedAddress().getId()).isEqualTo(data(response).path("addressId").asLong());
    }

    @Test
    @DisplayName("같은 마트 지역의 새 주소를 선택하면 마트 변경은 false이다")
    void keepsMartChangedFalseForSameMart() throws Exception {
        // given
        request(body("1111014000"), token);
        Map<String, Object> body = body("1111014000");
        body.put("select", true);

        // when
        MockHttpServletResponse response = request(body, token);

        // then
        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(data(response).path("selected").asBoolean()).isTrue();
        assertThat(data(response).path("martChanged").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("서비스 지역 밖 주소도 저장하며 선택 시 기존 이용 마트를 비운다")
    void selectsUnsupportedAddressWithoutMart() throws Exception {
        // given
        request(body("1111014000"), token);
        Map<String, Object> body = body("9999999999");
        body.put("select", true);

        // when
        MockHttpServletResponse response = request(body, token);

        // then
        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(data(response).path("serviceAvailable").asBoolean()).isFalse();
        assertThat(data(response).path("mart").isNull()).isTrue();
        assertThat(data(response).path("martChanged").asBoolean()).isTrue();
        assertThat(member.getMartId()).isNull();
        assertThat(addresses.countByMemberId(member.getId())).isEqualTo(2);
    }

    @Test
    @DisplayName("첫 주소가 서비스 지역 밖이면 선택하되 마트 변경은 없다")
    void allowsUnsupportedFirstAddress() throws Exception {
        // given
        Map<String, Object> body = body("9999999999");
        body.put("select", false);

        // when
        MockHttpServletResponse response = request(body, token);

        // then
        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(data(response).path("selected").asBoolean()).isTrue();
        assertThat(data(response).path("martChanged").asBoolean()).isFalse();
        assertThat(data(response).path("mart").isNull()).isTrue();
    }

    @Test
    @DisplayName("운영 중지된 마트의 지역에도 주소를 저장할 수 있다")
    void savesAddressInClosedMartRegion() throws Exception {
        // given
        jdbc.update("UPDATE mart SET status = 'CLOSED' WHERE id = 1");

        // when
        MockHttpServletResponse response = request(body("1111014000"), token);

        // then
        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(data(response).path("serviceAvailable").asBoolean()).isFalse();
        assertThat(data(response).path("mart").isNull()).isTrue();
    }

    @Test
    @DisplayName("위치 동의는 최초 일시만 기록하고 재동의와 false로 변경되지 않는다")
    void preservesFirstLocationConsent() throws Exception {
        // given
        Map<String, Object> agreed = body("1111014000");
        agreed.put("locationAgreed", true);
        request(agreed, token);
        LocalDateTime first = member.getLocationAgreedAt();

        // when
        request(agreed, token);
        Map<String, Object> declined = body("1111014000");
        declined.put("locationAgreed", false);
        request(declined, token);

        // then
        assertThat(first).isNotNull();
        assertThat(member.getLocationAgreedAt()).isEqualTo(first);
    }

    @Test
    @DisplayName("5개 등록 후에는 선택·동의 기록을 바꾸지 않고 ADDRESS-002를 반환한다")
    void rejectsSixthAddressWithoutChanges() throws Exception {
        // given
        for (int i = 0; i < 5; i++) {
            request(body("1111014000"), token);
        }
        Long selected = member.getSelectedAddress().getId();
        Map<String, Object> body = body("4121010300");
        body.put("select", true);
        body.put("locationAgreed", true);

        // when
        MockHttpServletResponse response = request(body, token);

        // then
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(mapper.readTree(response.getContentAsString()).path("code").asText()).isEqualTo("ADDRESS-002");
        assertThat(addresses.countByMemberId(member.getId())).isEqualTo(5);
        assertThat(member.getSelectedAddress().getId()).isEqualTo(selected);
        assertThat(member.getLocationAgreedAt()).isNull();
        assertThat(member.getMartId()).isEqualTo(1L);
    }

    @ParameterizedTest
    @MethodSource("invalidFields")
    @DisplayName("주소 요청 형식 위반은 저장 없이 COMMON-001을 반환한다")
    void validatesInput(String field, Object value) throws Exception {
        // given
        Map<String, Object> body = body("1111014000");
        body.put(field, value);

        // when
        MockHttpServletResponse response = request(body, token);

        // then
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(mapper.readTree(response.getContentAsString()).path("code").asText()).isEqualTo("COMMON-001");
        assertThat(addresses.countByMemberId(member.getId())).isZero();
    }

    @Test
    @DisplayName("본문에 다른 회원 ID가 있어도 토큰의 본인에게만 주소를 등록한다")
    void ignoresSuppliedMemberId() throws Exception {
        // given
        Member other = members.saveAndFlush(Member.builder().email(UUID.randomUUID() + "@other.test")
                .phone(UUID.randomUUID().toString().substring(0, 20)).name("다른 회원")
                .passwordHash("test-password-hash").build());
        Map<String, Object> body = body("1111014000");
        body.put("memberId", other.getId());

        // when
        MockHttpServletResponse response = request(body, token);

        // then
        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(addresses.countByMemberId(member.getId())).isEqualTo(1);
        assertThat(addresses.countByMemberId(other.getId())).isZero();
        assertThat(other.getSelectedAddress()).isNull();
        assertThat(other.getMartId()).isNull();
    }

    @Test
    @DisplayName("없는 회원은 AUTH-011이고 관리자와 비로그인은 등록할 수 없다")
    void enforcesMemberAuthentication() throws Exception {
        // given
        String missing = tokens.createMemberTokens(Long.MAX_VALUE).accessToken();
        String admin = tokens.createTokens(new AuthPrincipal(SubjectType.ADMIN, 1L, "SUPER_ADMIN", null)).accessToken();

        // when
        MockHttpServletResponse missingResponse = request(body("1111014000"), missing);
        MockHttpServletResponse adminResponse = request(body("1111014000"), admin);
        MockHttpServletResponse anonymous = request(body("1111014000"), null);

        // then
        assertThat(missingResponse.getStatus()).isEqualTo(401);
        assertThat(mapper.readTree(missingResponse.getContentAsString()).path("code").asText())
                .isEqualTo("AUTH-011");
        assertThat(adminResponse.getStatus()).isEqualTo(403);
        assertThat(anonymous.getStatus()).isEqualTo(401);
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
        var request = post("/api/members/me/addresses").contentType(MediaType.APPLICATION_JSON)
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
