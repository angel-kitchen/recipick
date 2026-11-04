package com.recipick.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipick.IntegrationTestSupport;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class MemberDemoSeedSqlIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper mapper;

    @BeforeEach
    void initializeDemoMembers() {
        seed();
    }

    @ParameterizedTest
    @CsvSource({"test01@reci.pick,false", "test02@reci.pick,true"})
    @DisplayName("공용 계정은 지정 비밀번호로 로그인하고 배송지와 멤버십 유무가 구분된다")
    void loginWithExpectedMembership(String email, boolean active) throws Exception {
        // given
        String hash = jdbc.queryForObject("SELECT password_hash FROM member WHERE email = ?", String.class, email);
        assertThat(hash).isNotEqualTo("test1234");

        // when
        var login = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("email", email, "password", "test1234"))))
                .andReturn().getResponse();

        // then
        assertThat(login.getStatus()).isEqualTo(200);
        String token = mapper.readTree(login.getContentAsString()).path("data").path("accessToken").asText();
        var me = mockMvc.perform(get("/api/members/me").header("Authorization", "Bearer " + token))
                .andReturn().getResponse();
        assertThat(me.getStatus()).isEqualTo(200);
        JsonNode profile = mapper.readTree(me.getContentAsString()).path("data");
        assertThat(profile.path("email").asText()).isEqualTo(email);
        assertThat(profile.path("membership").path("active").asBoolean()).isEqualTo(active);
        assertThat(profile.path("currentAddress").path("legalDongCode").asText()).isEqualTo("1111014000");
        assertThat(profile.path("currentAddress").path("serviceAvailable").asBoolean()).isTrue();
        if (active) {
            var response = mockMvc.perform(get("/api/memberships/me").header("Authorization", "Bearer " + token))
                    .andReturn().getResponse();
            JsonNode current = mapper.readTree(response.getContentAsString()).path("data").path("current");
            assertThat(current.path("source").asText()).isEqualTo("ADMIN_GRANT");
            assertThat(current.path("autoRenew").asBoolean()).isFalse();
            assertThat(Duration.between(OffsetDateTime.parse(current.path("startAt").asText()),
                    OffsetDateTime.parse(current.path("endAt").asText())).toDays()).isEqualTo(30);
        }
    }

    @Test
    @DisplayName("시드를 다시 실행해도 계정·배송지·이용권을 중복 생성하거나 변경된 상태를 초기화하지 않는다")
    void repeatedSeedPreservesExistingState() {
        // given
        Integer addresses = jdbc.queryForObject("""
                SELECT COUNT(*) FROM member_address a JOIN member m ON m.id = a.member_id
                WHERE m.email IN ('test01@reci.pick', 'test02@reci.pick')
                """, Integer.class);
        jdbc.update("UPDATE member SET password_hash = 'changed-hash' WHERE email = 'test01@reci.pick'");
        jdbc.update("""
                UPDATE membership ms JOIN member m ON m.id = ms.member_id
                SET ms.terminated_at = ms.start_at, ms.end_at = DATE_ADD(ms.start_at, INTERVAL 1 DAY)
                WHERE m.email = 'test02@reci.pick'
                """);

        // when
        seed();

        // then
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM member WHERE email IN (?, ?)", Integer.class,
                "test01@reci.pick", "test02@reci.pick")).isEqualTo(2);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM member_address a JOIN member m ON m.id = a.member_id
                WHERE m.email IN ('test01@reci.pick', 'test02@reci.pick')
                """, Integer.class)).isEqualTo(addresses);
        assertThat(jdbc.queryForObject("SELECT password_hash FROM member WHERE email = 'test01@reci.pick'",
                String.class)).isEqualTo("changed-hash");
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM membership ms JOIN member m ON m.id = ms.member_id
                WHERE m.email = 'test02@reci.pick' AND ms.terminated_at IS NOT NULL
                  AND TIMESTAMPDIFF(DAY, ms.start_at, ms.end_at) = 1
                """, Integer.class)).isEqualTo(1);
    }

    private void seed() {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(new ClassPathResource("data-member-demo.sql"));
        populator.setSqlScriptEncoding("UTF-8");
        populator.execute(dataSource);
    }
}
