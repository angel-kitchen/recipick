package com.recipick.domain.recipe.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipick.IntegrationTestSupport;
import com.recipick.domain.member.entity.Admin;
import com.recipick.domain.member.entity.AdminAuditLog;
import com.recipick.domain.member.entity.enums.AdminRole;
import com.recipick.domain.member.repository.AdminAuditLogRepository;
import com.recipick.domain.member.repository.AdminRepository;
import com.recipick.domain.recipe.entity.AiRequestLog;
import com.recipick.domain.recipe.entity.AiRequestType;
import com.recipick.domain.recipe.entity.AiResultStatus;
import com.recipick.domain.recipe.repository.AiRequestLogRepository;
import com.recipick.global.audit.AdminAuditAction;
import com.recipick.global.audit.AdminAuditTarget;
import com.recipick.global.security.AuthPrincipal;
import com.recipick.global.security.JwtTokenProvider;
import com.recipick.global.security.SubjectType;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

// 실제 MySQL·보안 필터·감사 기록까지 태워 검증한다. MockMvc가 테스트와 같은 스레드에서 돌아 Facade 트랜잭션이
// 테스트 트랜잭션에 합류하므로 데이터와 감사 기록은 끝나면 롤백된다. 다른 이력과 섞이지 않게 2020-01 구간만 조회한다
@Transactional
class AiRequestLogAdminControllerIntegrationTest extends IntegrationTestSupport {

    private static final String URL = "/api/admin/ai-requests";
    private static final String PERIOD = "from=2020-01-01&to=2020-01-03";
    private static final Long MEMBER_ID = 9504L;

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
    @Autowired
    private AiRequestLogRepository aiRequestLogRepository;
    @Autowired
    private EntityManager entityManager;

    private Long superAdminId;
    private String superToken;

    // 01-01 자연어 장보기 성공 / 01-02 조건 추천 성공 / 01-03 23:59:59 자연어 장보기 실패 / 01-04 00:00 기간 밖
    private Long autoCartPreviewedId;
    private Long recommendedId;
    private Long fallbackId;
    private Long outOfPeriodId;

    @BeforeEach
    void setUp() {
        // 감사 기록은 토큰의 관리자 ID로 ADMIN 행을 다시 확인하므로 활성 SUPER_ADMIN을 실제로 저장한다
        Admin superAdmin = adminRepository.saveAndFlush(Admin.builder()
            .loginId(UUID.randomUUID().toString())
            .name("AI 이력 조회 슈퍼 관리자")
            .passwordHash("hash")
            .role(AdminRole.SUPER_ADMIN)
            .active(true)
            .build());
        superAdminId = superAdmin.getId();
        superToken = token(superAdminId, "SUPER_ADMIN", null);

        autoCartPreviewedId = saveLog(AiRequestType.AUTO_CART, AiResultStatus.PREVIEWED,
            "{\"menus\":[{\"mention\":\"김치찌개\"}]}", "1,2", null, LocalDateTime.of(2020, 1, 1, 9, 0));
        recommendedId = saveLog(AiRequestType.RECOMMENDATION, AiResultStatus.RECOMMENDED,
            "{\"conditions\":{\"dishType\":\"STEW\"}}", "3", null, LocalDateTime.of(2020, 1, 2, 9, 0));
        fallbackId = saveLog(AiRequestType.AUTO_CART, AiResultStatus.FALLBACK,
            null, null, "TIMEOUT", LocalDateTime.of(2020, 1, 3, 23, 59, 59));
        outOfPeriodId = saveLog(AiRequestType.AUTO_CART, AiResultStatus.NO_MATCH,
            "{\"menus\":[]}", null, null, LocalDateTime.of(2020, 1, 4, 0, 0));
        // SQL로 바꾼 생성 시각이 조회에 보이도록, 영속성 컨텍스트에 남은 이전 값을 비운다
        entityManager.clear();
    }

    private Long saveLog(AiRequestType type, AiResultStatus status, String parsedResult, String recipeIds,
                         String failureReason, LocalDateTime createdAt) {
        AiRequestLog log = aiRequestLogRepository.saveAndFlush(AiRequestLog.builder()
            .memberId(MEMBER_ID)
            .requestType(type)
            .rawText("원문 " + status)
            .parsedResult(parsedResult)
            .matchedRecipeIds(recipeIds)
            .resultStatus(status)
            .failureReason(failureReason)
            .model("claude-opus-5-5")
            .inputTokens(parsedResult == null ? null : 900)
            .outputTokens(parsedResult == null ? null : 120)
            .cacheReadTokens(parsedResult == null ? null : 800)
            .latencyMs(1500)
            .build());
        // created_at은 생성 뒤 바꿀 수 없는 컬럼이라 SQL로 직접 과거 시각을 넣는다
        entityManager.createNativeQuery("UPDATE ai_request_log SET created_at = :createdAt WHERE id = :id")
            .setParameter("createdAt", createdAt)
            .setParameter("id", log.getId())
            .executeUpdate();
        return log.getId();
    }

    @Test
    @DisplayName("기간 안의 이력을 최신순으로 원문·분석 결과·사용량과 함께 보여 주고 감사 로그를 남긴다")
    void findAiRequests_returnsLatestFirstAndRecordsAudit() throws Exception {
        // when
        MockHttpServletResponse response = request(superToken, URL + "?" + PERIOD);

        // then
        assertThat(response.getStatus()).isEqualTo(200);
        JsonNode data = body(response).path("data");
        assertThat(ids(data)).containsExactly(fallbackId, recommendedId, autoCartPreviewedId);
        assertThat(data.path("totalElements").asLong()).isEqualTo(3);

        JsonNode previewed = data.path("content").get(2);
        assertThat(previewed.path("rawText").asText()).isEqualTo("원문 PREVIEWED");
        assertThat(previewed.path("parsedResult").path("menus").get(0).path("mention").asText()).isEqualTo("김치찌개");
        assertThat(previewed.path("matchedRecipeIds")).extracting(JsonNode::asLong).containsExactly(1L, 2L);
        assertThat(previewed.path("inputTokens").asInt()).isEqualTo(900);
        assertThat(previewed.path("latencyMs").asInt()).isEqualTo(1500);
        assertThat(previewed.path("createdAt").asText()).isEqualTo("2020-01-01T09:00:00+09:00");

        JsonNode fallback = data.path("content").get(0);
        assertThat(fallback.path("success").asBoolean()).isFalse();
        assertThat(fallback.path("failureReason").asText()).isEqualTo("TIMEOUT");
        assertThat(fallback.path("parsedResult").isNull()).isTrue();
        assertThat(fallback.path("matchedRecipeIds")).isEmpty();

        assertThat(auditsOfSuperAdmin()).singleElement().satisfies(audit -> {
            assertThat(audit.getAction()).isEqualTo(AdminAuditAction.AI_REQUEST_LIST_VIEW);
            assertThat(audit.getTargetType()).isEqualTo(AdminAuditTarget.AI_REQUEST);
            assertThat(audit.getTargetId()).isNull();
            assertThat(audit.getDetails()).isNull();
        });
    }

    @Test
    @DisplayName("요청 유형과 성공 여부로 거른다")
    void findAiRequests_filtersByTypeAndSuccess() throws Exception {
        // when
        JsonNode autoCartSuccess = body(request(superToken, URL + "?" + PERIOD + "&requestType=AUTO_CART&success=true"))
            .path("data");
        JsonNode recommendation = body(request(superToken, URL + "?" + PERIOD + "&requestType=RECOMMENDATION"))
            .path("data");
        JsonNode failed = body(request(superToken, URL + "?" + PERIOD + "&success=false")).path("data");

        // then
        assertThat(ids(autoCartSuccess)).containsExactly(autoCartPreviewedId);
        assertThat(ids(recommendation)).containsExactly(recommendedId);
        assertThat(ids(failed)).containsExactly(fallbackId);
    }

    @Test
    @DisplayName("결과가 없어도 실제 조회이므로 감사 로그를 남긴다")
    void findAiRequests_recordsAuditWhenEmpty() throws Exception {
        // when
        MockHttpServletResponse response = request(superToken, URL + "?from=2019-01-01&to=2019-01-01");

        // then
        assertThat(body(response).path("data").path("content")).isEmpty();
        assertThat(auditsOfSuperAdmin()).hasSize(1);
        assertThat(outOfPeriodId).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "requestType=CHAT",
        "from=2020-02-30",
        "from=2020-01-02&to=2020-01-01",
        "page=-1",
        "size=101"
    })
    @DisplayName("잘못된 유형·기간·페이징은 400 COMMON-001이고 감사 로그를 남기지 않는다")
    void findAiRequests_rejectsInvalidQuery(String query) throws Exception {
        // when
        MockHttpServletResponse response = request(superToken, URL + "?" + query);

        // then
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(body(response).path("code").asText()).isEqualTo("COMMON-001");
        assertThat(auditsOfSuperAdmin()).isEmpty();
    }

    @Test
    @DisplayName("MART_ADMIN은 403 AUTH-006이다")
    void findAiRequests_rejectsMartAdmin() throws Exception {
        // when
        MockHttpServletResponse response = request(token(Long.MAX_VALUE, "MART_ADMIN", 1L), URL);

        // then
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(body(response).path("code").asText()).isEqualTo("AUTH-006");
    }

    private String token(Long adminId, String role, Long martId) {
        return tokens.createTokens(new AuthPrincipal(SubjectType.ADMIN, adminId, role, martId)).accessToken();
    }

    private MockHttpServletResponse request(String token, String url) throws Exception {
        return mockMvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andReturn()
            .getResponse();
    }

    private JsonNode body(MockHttpServletResponse response) throws Exception {
        return objectMapper.readTree(response.getContentAsString(StandardCharsets.UTF_8));
    }

    private List<Long> ids(JsonNode data) {
        List<Long> ids = new ArrayList<>();
        data.path("content").forEach(row -> ids.add(row.path("aiRequestId").asLong()));
        return ids;
    }

    private List<AdminAuditLog> auditsOfSuperAdmin() {
        return auditLogRepository.findAll().stream()
            .filter(audit -> audit.getAdmin().getId().equals(superAdminId))
            .toList();
    }
}
