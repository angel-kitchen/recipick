package com.recipick.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipick.IntegrationTestSupport;
import com.recipick.domain.member.entity.Admin;
import com.recipick.domain.member.repository.AdminRepository;
import com.recipick.global.security.AuthPrincipal;
import com.recipick.global.security.JwtTokenProvider;
import com.recipick.global.security.SubjectType;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

class AdminSeedSqlIntegrationTest extends IntegrationTestSupport {

    private static final String TEST_PASSWORD = "Admin1234!";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private JwtTokenProvider tokens;
    @Autowired
    private AdminRepository admins;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbc;

    @ParameterizedTest
    @CsvSource({"super01,SUPER_ADMIN,", "samcheong01,MART_ADMIN,1", "haan01,MART_ADMIN,2"})
    @DisplayName("SQL 초기 관리자는 관리자 토큰으로 로그인하고 회원 로그인은 거절된다")
    void logsInOnlyThroughAdminEndpoint(String loginId, String role, Long martId) throws Exception {
        // given
        Admin admin = admins.findByLoginIdIgnoreCase(loginId).orElseThrow();

        // when
        MockHttpServletResponse response = mockMvc.perform(post("/api/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("loginId", loginId, "password", TEST_PASSWORD))))
                .andReturn().getResponse();
        MockHttpServletResponse memberResponse = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("email", loginId, "password", TEST_PASSWORD))))
                .andReturn().getResponse();

        // then
        assertThat(response.getStatus()).isEqualTo(200);
        JsonNode data = mapper.readTree(response.getContentAsString()).path("data");
        AuthPrincipal principal = tokens.parseAccessToken(data.path("accessToken").asText());
        assertThat(principal.type()).isEqualTo(SubjectType.ADMIN);
        assertThat(principal.id()).isEqualTo(admin.getId());
        assertThat(principal.role()).isEqualTo(role);
        assertThat(principal.martId()).isEqualTo(martId);
        assertThat(admin.getPasswordHash()).isNotEqualTo(TEST_PASSWORD);
        assertThat(admin.getCreatedAt()).isNotNull();
        assertThat(memberResponse.getStatus()).isEqualTo(401);
        assertThat(mapper.readTree(memberResponse.getContentAsString()).path("code").asText())
                .isEqualTo("AUTH-007");
        assertThat(memberResponse.getHeader("Set-Cookie")).isNull();
    }

    @ParameterizedTest
    @CsvSource({"samcheong01,1,900001", "haan01,2,900002"})
    @Transactional
    @DisplayName("기존 마트 ID가 달라도 법정동으로 찾아 관리자와 토큰에 실제 ID를 저장한다")
    void usesExistingMartId(String loginId, Long regionId, Long actualMartId) throws Exception {
        // given
        jdbc.update("DELETE FROM admin WHERE login_id = ?", loginId);
        jdbc.update("UPDATE mart SET region_id = NULL WHERE id = ?", regionId);
        jdbc.update("""
                INSERT INTO mart (id, region_id, name, latitude, longitude,
                                  service_radius_m, status, created_at, updated_at)
                SELECT ?, ?, name, latitude, longitude, service_radius_m, status, NOW(), NOW()
                FROM mart WHERE id = ?
                """, actualMartId, regionId, regionId);
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                new ClassPathResource("data.sql"), new ClassPathResource("data-admin-local.sql"));
        populator.setSqlScriptEncoding("UTF-8");

        // when
        populator.execute(dataSource);
        MockHttpServletResponse response = mockMvc.perform(post("/api/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("loginId", loginId, "password", TEST_PASSWORD))))
                .andReturn().getResponse();

        // then
        assertThat(response.getStatus()).isEqualTo(200);
        JsonNode data = mapper.readTree(response.getContentAsString()).path("data");
        assertThat(data.path("martId").asLong()).isEqualTo(actualMartId);
        assertThat(tokens.parseAccessToken(data.path("accessToken").asText()).martId()).isEqualTo(actualMartId);
        assertThat(jdbc.queryForObject("SELECT mart_id FROM admin WHERE login_id = ?", Long.class, loginId))
                .isEqualTo(actualMartId);
    }

    @Test
    @Transactional
    @DisplayName("SQL을 다시 실행해도 관리자 중복 생성과 기존 정보 덮어쓰기가 없다")
    void preservesExistingAccountsOnRepeatedInitialization() {
        // given
        long count = admins.count();
        Admin admin = admins.findByLoginIdIgnoreCase("super01").orElseThrow();
        jdbc.update("UPDATE admin SET password_hash = ?, active = false WHERE id = ?", "changed-hash", admin.getId());
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                new ClassPathResource("data-admin-local.sql"));
        populator.setSqlScriptEncoding("UTF-8");

        // when
        populator.execute(dataSource);
        populator.execute(dataSource);

        // then
        assertThat(admins.count()).isEqualTo(count);
        assertThat(jdbc.queryForObject("SELECT password_hash FROM admin WHERE id = ?", String.class, admin.getId()))
                .isEqualTo("changed-hash");
        assertThat(jdbc.queryForObject("SELECT active FROM admin WHERE id = ?", Boolean.class, admin.getId()))
                .isFalse();
    }
}
