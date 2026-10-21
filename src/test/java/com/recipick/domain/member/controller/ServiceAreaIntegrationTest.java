package com.recipick.domain.member.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipick.IntegrationTestSupport;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class ServiceAreaIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private JdbcTemplate jdbc;

    @ParameterizedTest
    @CsvSource({"1111014000,삼청 마트", "4121010300,하안 마트"})
    @DisplayName("비로그인으로 서비스 지역의 운영 마트 이름만 확인할 수 있다")
    void checksOpenMartWithoutAuthentication(String code, String name) throws Exception {
        // given
        String body = mapper.writeValueAsString(Map.of("legalDongCode", code));

        // when
        MockHttpServletResponse response = request(body);

        // then
        assertThat(response.getStatus()).isEqualTo(200);
        JsonNode json = mapper.readTree(response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(json.path("success").asBoolean()).isTrue();
        assertThat(json.path("code").asText()).isEqualTo("OK");
        assertThat(json.path("data").path("available").asBoolean()).isTrue();
        assertThat(json.path("data").path("martName").asText()).isEqualTo(name);
        assertThat(json.path("data").size()).isEqualTo(2);
        assertThat(response.getHeader("Set-Cookie")).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"9999999999", "0000000000"})
    @DisplayName("형식이 맞는 미등록 법정동은 오류 대신 이용 불가로 응답한다")
    void returnsUnavailableOutsideServiceArea(String code) throws Exception {
        // given
        String body = mapper.writeValueAsString(Map.of("legalDongCode", code));

        // when
        MockHttpServletResponse response = request(body);

        // then
        assertUnavailable(response);
    }

    @Test
    @DisplayName("운영이 중지된 마트는 서비스 가능 지역으로 표시하지 않는다")
    void excludesClosedMart() throws Exception {
        // given
        jdbc.update("UPDATE mart SET status = 'CLOSED' WHERE region_id = 1");

        // when
        MockHttpServletResponse response = request("{\"legalDongCode\":\"1111014000\"}");

        // then
        assertUnavailable(response);
    }

    @Test
    @DisplayName("등록된 지역에 마트가 없어도 이용 불가로 응답한다")
    void handlesRegionWithoutMart() throws Exception {
        // given
        jdbc.update("INSERT INTO region (legal_dong_code, name) VALUES ('9999999998', '마트 없는 지역')");

        // when
        MockHttpServletResponse response = request("{\"legalDongCode\":\"9999999998\"}");

        // then
        assertUnavailable(response);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "111101400", "11110140000", "abcdefghij", "11110-4000", "１１１１０１４０００"})
    @DisplayName("법정동 코드 누락과 숫자 10자리 형식 위반은 입력 오류다")
    void rejectsInvalidCode(String code) throws Exception {
        // given
        String body = mapper.writeValueAsString(java.util.Collections.singletonMap("legalDongCode", code));

        // when
        MockHttpServletResponse response = request(body);

        // then
        assertThat(response.getStatus()).isEqualTo(400);
        JsonNode json = mapper.readTree(response.getContentAsString());
        assertThat(json.path("code").asText()).isEqualTo("COMMON-001");
        assertThat(json.path("errors").get(0).path("field").asText()).isEqualTo("legalDongCode");
    }

    @Test
    @DisplayName("서비스 지역 확인은 회원 정보를 생성하거나 변경하지 않는다")
    void doesNotSaveMemberData() throws Exception {
        // given
        var members = jdbc.queryForList("SELECT * FROM member ORDER BY id");
        var addresses = jdbc.queryForList("SELECT * FROM member_address ORDER BY id");

        // when
        MockHttpServletResponse response = request("{\"legalDongCode\":\"1111014000\"}");

        // then
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(jdbc.queryForList("SELECT * FROM member ORDER BY id")).isEqualTo(members);
        assertThat(jdbc.queryForList("SELECT * FROM member_address ORDER BY id")).isEqualTo(addresses);
    }

    private MockHttpServletResponse request(String body) throws Exception {
        return mockMvc.perform(post("/api/service-area/check").contentType(MediaType.APPLICATION_JSON)
                .content(body)).andReturn().getResponse();
    }

    private void assertUnavailable(MockHttpServletResponse response) throws Exception {
        assertThat(response.getStatus()).isEqualTo(200);
        JsonNode json = mapper.readTree(response.getContentAsString());
        assertThat(json.path("success").asBoolean()).isTrue();
        assertThat(json.path("data").path("available").asBoolean()).isFalse();
        assertThat(json.path("data").path("martName").isNull()).isTrue();
    }
}
