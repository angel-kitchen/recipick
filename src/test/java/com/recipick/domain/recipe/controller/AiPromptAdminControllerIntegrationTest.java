package com.recipick.domain.recipe.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipick.IntegrationTestSupport;
import com.recipick.domain.member.entity.Admin;
import com.recipick.domain.member.entity.AdminAuditLog;
import com.recipick.domain.member.entity.enums.AdminRole;
import com.recipick.domain.member.repository.AdminAuditLogRepository;
import com.recipick.domain.member.repository.AdminRepository;
import com.recipick.domain.recipe.entity.AiPromptType;
import com.recipick.global.audit.AdminAuditAction;
import com.recipick.global.audit.AdminAuditTarget;
import com.recipick.global.security.AuthPrincipal;
import com.recipick.global.security.JwtTokenProvider;
import com.recipick.global.security.SubjectType;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

// 실제 MySQL·보안 필터·감사 기록까지 태워 검증한다. MockMvc가 테스트와 같은 스레드에서 돌아
// Facade 트랜잭션이 테스트 트랜잭션에 합류하므로 저장한 버전과 감사 기록은 끝나면 롤백된다
@Transactional
class AiPromptAdminControllerIntegrationTest extends IntegrationTestSupport {

    private static final String URL = "/api/admin/ai-prompts";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JwtTokenProvider tokens;
    @Autowired
    private AdminRepository adminRepository;
    @Autowired
    private AdminAuditLogRepository auditLogRepository;

    private Long superAdminId;
    private String superToken;

    @BeforeEach
    void setUp() {
        // 감사 기록은 토큰의 관리자 ID로 ADMIN 행을 다시 확인하므로 활성 SUPER_ADMIN을 실제로 저장한다
        Admin superAdmin = adminRepository.saveAndFlush(Admin.builder()
            .loginId(UUID.randomUUID().toString())
            .name("프롬프트 관리 슈퍼 관리자")
            .passwordHash("hash")
            .role(AdminRole.SUPER_ADMIN)
            .active(true)
            .build());
        superAdminId = superAdmin.getId();
        superToken = token(superAdminId, "SUPER_ADMIN", null);
    }

    @Test
    @DisplayName("새 버전은 종류별로 1부터 번호를 매겨 비활성으로 저장하고, 종류·버전만 감사 로그에 남긴다")
    void createPrompt_savesInactiveVersionsAndRecordsAudit() throws Exception {
        // when
        MockHttpServletResponse first = create(AiPromptType.MENU, "첫 지시문", "처음 저장");
        MockHttpServletResponse second = create(AiPromptType.MENU, "둘째 지시문", null);

        // then
        assertThat(first.getStatus()).isEqualTo(201);
        JsonNode created = body(second).path("data");
        assertThat(created.path("version").asInt()).isEqualTo(2);
        assertThat(created.path("active").asBoolean()).isFalse();
        assertThat(created.path("createdByAdminId").asLong()).isEqualTo(superAdminId);

        JsonNode prompts = body(send(get(URL + "?type=MENU"))).path("data");
        assertThat(prompts.path("activeVersion").isNull()).isTrue();
        assertThat(prompts.path("defaultContent").asText()).isEqualTo(AiPromptType.MENU.defaultContent());
        assertThat(prompts.path("versions")).extracting(version -> version.path("version").asInt())
            .containsExactly(2, 1);

        assertThat(audits()).hasSize(2).allSatisfy(audit -> {
            assertThat(audit.getAction()).isEqualTo(AdminAuditAction.AI_PROMPT_CREATE);
            assertThat(audit.getTargetType()).isEqualTo(AdminAuditTarget.AI_PROMPT);
            assertThat(audit.getDetails()).containsOnlyKeys("type", "version");
        });
    }

    @Test
    @DisplayName("활성화하면 그 버전만 켜고, 바뀐 버전 번호를 감사 로그에 남긴다")
    void activatePrompt_switchesActiveVersion() throws Exception {
        // given
        long first = promptId(create(AiPromptType.CONDITION, "첫 지시문", null));
        long second = promptId(create(AiPromptType.CONDITION, "둘째 지시문", null));
        send(post(URL + "/" + first + "/activate"));

        // when
        MockHttpServletResponse response = send(post(URL + "/" + second + "/activate"));

        // then
        assertThat(body(response).path("data").path("activeVersion").asInt()).isEqualTo(2);
        JsonNode prompts = body(send(get(URL + "?type=CONDITION"))).path("data");
        assertThat(prompts.path("activeVersion").asInt()).isEqualTo(2);
        assertThat(prompts.path("versions")).extracting(version -> version.path("active").asBoolean())
            .containsExactly(true, false);

        assertThat(activeChanges()).extracting(AdminAuditLog::getTargetId, audit -> audit.getDetails().get("version"))
            .containsExactly(
                tuple(first, versionChange(null, 1)),
                tuple(second, versionChange(1, 2)));
    }

    @Test
    @DisplayName("이미 활성인 버전을 다시 활성화하면 변경 없이 성공하고 감사 로그를 남기지 않는다")
    void activatePrompt_skipsAuditWhenAlreadyActive() throws Exception {
        // given
        long promptId = promptId(create(AiPromptType.RECOMMEND, "지시문", null));
        send(post(URL + "/" + promptId + "/activate"));

        // when
        MockHttpServletResponse response = send(post(URL + "/" + promptId + "/activate"));

        // then
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(activeChanges()).hasSize(1);
    }

    @Test
    @DisplayName("되돌리면 기본 지시문을 쓰고, 활성 버전이 없을 때 다시 되돌리면 감사 로그를 남기지 않는다")
    void resetPrompt_usesDefaultContent() throws Exception {
        // given
        long promptId = promptId(create(AiPromptType.FRIDGE, "지시문", null));
        send(post(URL + "/" + promptId + "/activate"));

        // when
        MockHttpServletResponse response = send(post(URL + "/types/FRIDGE/reset"));
        send(post(URL + "/types/FRIDGE/reset"));

        // then
        assertThat(body(response).path("data").path("activeVersion").isNull()).isTrue();
        assertThat(body(send(get(URL + "?type=FRIDGE"))).path("data").path("activeVersion").isNull()).isTrue();
        assertThat(activeChanges()).extracting(audit -> audit.getDetails().get("version"))
            .containsExactly(versionChange(null, 1), versionChange(1, null));
    }

    @Test
    @DisplayName("없는 버전을 활성화하면 AI-002로 응답한다")
    void activatePrompt_failsWhenMissing() throws Exception {
        // when
        MockHttpServletResponse response = send(post(URL + "/" + Long.MAX_VALUE + "/activate"));

        // then
        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(body(response).path("code").asText()).isEqualTo("AI-002");
    }

    @Test
    @DisplayName("지시문이 비어 있으면 입력값 검증 오류로 거절한다")
    void createPrompt_rejectsBlankContent() throws Exception {
        // when
        MockHttpServletResponse response = create(AiPromptType.MENU, " ", null);

        // then
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(body(response).path("code").asText()).isEqualTo("COMMON-001");
    }

    @Test
    @DisplayName("마트 관리자는 프롬프트를 볼 수 없다")
    void findPrompts_rejectsMartAdmin() throws Exception {
        // when
        MockHttpServletResponse response = mockMvc.perform(get(URL + "?type=MENU")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(martAdminForRoleTest(), "MART_ADMIN", 1L)))
            .andReturn()
            .getResponse();

        // then
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(body(response).path("code").asText()).isEqualTo("AUTH-006");
    }

    private MockHttpServletResponse create(AiPromptType type, String content, String memo) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("type", type.name());
        request.put("content", content);
        request.put("memo", memo);
        return send(post(URL)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)));
    }

    private MockHttpServletResponse send(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + superToken))
            .andReturn()
            .getResponse();
    }

    private long promptId(MockHttpServletResponse response) throws Exception {
        return body(response).path("data").path("promptId").asLong();
    }

    private String token(Long adminId, String role, Long martId) {
        return testAdminTokens(new AuthPrincipal(SubjectType.ADMIN, adminId, role, martId)).accessToken();
    }

    private JsonNode body(MockHttpServletResponse response) throws Exception {
        return objectMapper.readTree(response.getContentAsString(StandardCharsets.UTF_8));
    }

    private List<AdminAuditLog> audits() {
        return auditLogRepository.findAll().stream()
            .filter(audit -> audit.getAdmin().getId().equals(superAdminId))
            .toList();
    }

    private List<AdminAuditLog> activeChanges() {
        return audits().stream()
            .filter(audit -> audit.getAction() == AdminAuditAction.AI_PROMPT_ACTIVE_CHANGE)
            .toList();
    }

    private static Map<String, Object> versionChange(Integer before, Integer after) {
        Map<String, Object> change = new HashMap<>();
        change.put("before", before);
        change.put("after", after);
        return change;
    }

    private Long martAdminForRoleTest() {
        return adminRepository.saveAndFlush(Admin.builder().loginId(UUID.randomUUID().toString())
                .passwordHash("unused").role(AdminRole.MART_ADMIN).martId(1L).active(true).build()).getId();
    }
}
