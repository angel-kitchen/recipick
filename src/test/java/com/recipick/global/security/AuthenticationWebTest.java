package com.recipick.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipick.domain.recipe.controller.RecipeBookmarkController;
import com.recipick.domain.recipe.controller.RecipeController;
import com.recipick.domain.recipe.service.RecipeBookmarkService;
import com.recipick.domain.recipe.service.RecipeService;
import com.recipick.global.config.ClockConfig;
import com.recipick.global.config.JacksonConfig;
import com.recipick.global.config.JwtConfig;
import com.recipick.global.config.SecurityConfig;
import com.recipick.global.config.WebConfig;
import com.recipick.global.exception.GlobalExceptionHandler;
import com.recipick.global.response.ApiResponse;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Encoders;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = {RecipeController.class, RecipeBookmarkController.class},
        properties = "security.cors.allowed-origins=http://localhost:3000")
@ActiveProfiles("test")
@Import({SecurityConfig.class, JwtConfig.class, JwtTokenProvider.class, ClockConfig.class,
        WebConfig.class, LoginPrincipalArgumentResolver.class, SecurityErrorResponseWriter.class,
        JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class, JacksonConfig.class,
        GlobalExceptionHandler.class, AuthenticationWebTest.ProbeController.class})
class AuthenticationWebTest {

    private static final String SECRET = Encoders.BASE64.encode(Jwts.SIG.HS256.key().build().getEncoded());

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        registry.add("security.jwt.secret", () -> SECRET);
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private SecretKey jwtSigningKey;
    @MockitoSpyBean
    private JwtTokenProvider tokenProvider;
    @MockitoBean
    private RecipeService recipeService;
    @MockitoBean
    private RecipeBookmarkService bookmarkService;

    @Test
    @DisplayName("보호 API에 토큰 없이 접근하면 공통 AUTH-001 응답을 반환한다")
    void requiresLogin() throws Exception {
        // given
        String path = "/api/auth-test/member";

        // when
        MockHttpServletResponse response = mockMvc.perform(get(path)).andReturn().getResponse();

        // then
        assertError(response, 401, "AUTH-001");
    }

    @Test
    @DisplayName("유효한 회원 토큰은 한 번만 파싱하고 두 식별자 모두 Principal에서 꺼낸다")
    void parsesTokenOnce() throws Exception {
        // given
        String token = tokenProvider.createMemberTokens(17L).accessToken();

        // when
        MockHttpServletResponse response = mockMvc.perform(get("/api/auth-test/member")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).andReturn().getResponse();

        // then
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(objectMapper.readTree(response.getContentAsString()).path("data").get(0).asLong()).isEqualTo(17L);
        assertThat(objectMapper.readTree(response.getContentAsString()).path("data").get(1).asLong()).isEqualTo(17L);
        verify(tokenProvider, times(1)).parseAccessToken(token);
    }

    @Test
    @DisplayName("만료된 토큰은 AUTH-003으로 반환한다")
    void rejectsExpiredToken() throws Exception {
        // given
        String token = signedToken(Map.of("type", "MEMBER"), Instant.now().minusSeconds(10));

        // when
        MockHttpServletResponse response = mockMvc.perform(get("/api/auth-test/member")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).andReturn().getResponse();

        // then
        assertError(response, 401, "AUTH-003");
    }

    @Test
    @DisplayName("리프레시 토큰을 Bearer로 보내면 AUTH-002로 반환한다")
    void rejectsRefreshToken() throws Exception {
        // given
        String token = tokenProvider.createMemberTokens(17L).refreshToken();

        // when
        MockHttpServletResponse response = mockMvc.perform(get("/api/auth-test/member")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).andReturn().getResponse();

        // then
        assertError(response, 401, "AUTH-002");
    }

    @Test
    @DisplayName("잘못된 인증 헤더와 중복 인증 헤더는 AUTH-002로 반환한다")
    void rejectsInvalidHeaders() throws Exception {
        // given
        String token = tokenProvider.createMemberTokens(17L).accessToken();

        // when
        MockHttpServletResponse malformed = mockMvc.perform(get("/api/auth-test/member")
                .header(HttpHeaders.AUTHORIZATION, "Basic abc")).andReturn().getResponse();
        MockHttpServletResponse duplicate = mockMvc.perform(get("/api/auth-test/member")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token, "Bearer " + token)).andReturn().getResponse();

        // then
        assertError(malformed, 401, "AUTH-002");
        assertError(duplicate, 401, "AUTH-002");
    }

    @Test
    @DisplayName("회원 토큰으로 관리자 API를 호출하면 AUTH-004로 거절한다")
    void rejectsMemberOnAdminPath() throws Exception {
        // given
        String token = tokenProvider.createMemberTokens(17L).accessToken();

        // when
        MockHttpServletResponse response = mockMvc.perform(get("/api/admin/auth-test/id")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).andReturn().getResponse();

        // then
        assertError(response, 403, "AUTH-004");
    }

    @Test
    @DisplayName("관리자는 관리자 ID를 얻지만 회원 API를 호출할 수 없다")
    void isolatesAdminAndMember() throws Exception {
        // given
        String token = adminToken("SUPER_ADMIN", null);

        // when
        MockHttpServletResponse admin = mockMvc.perform(get("/api/admin/auth-test/id")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).andReturn().getResponse();
        MockHttpServletResponse member = mockMvc.perform(get("/api/auth-test/member")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).andReturn().getResponse();

        // then
        assertThat(admin.getStatus()).isEqualTo(200);
        assertThat(objectMapper.readTree(admin.getContentAsString()).path("data").asLong()).isEqualTo(17L);
        assertError(member, 403, "AUTH-004");
    }

    @Test
    @DisplayName("마트 관리자는 슈퍼 관리자 전용 API에서 AUTH-006으로 거절한다")
    void enforcesSuperAdminAuthority() throws Exception {
        // given
        String token = adminToken("MART_ADMIN", 3L);

        // when
        MockHttpServletResponse response = mockMvc.perform(get("/api/admin/auth-test/super")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).andReturn().getResponse();

        // then
        assertError(response, 403, "AUTH-006");
    }

    @Test
    @DisplayName("다음 요청에 토큰이 없으면 이전 요청의 인증 정보가 남지 않는다")
    void clearsAuthenticationBetweenRequests() throws Exception {
        // given
        String token = tokenProvider.createMemberTokens(17L).accessToken();
        mockMvc.perform(get("/api/auth-test/member").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));

        // when
        MockHttpServletResponse response = mockMvc.perform(get("/api/auth-test/member")).andReturn().getResponse();

        // then
        assertError(response, 401, "AUTH-001");
    }

    @Test
    @DisplayName("레시피 요청의 임시 회원 헤더는 무시하고 토큰의 회원 ID로 조회한다")
    void ignoresSpoofedMemberIdHeader() throws Exception {
        // given
        String token = tokenProvider.createMemberTokens(17L).accessToken();

        // when
        MockHttpServletResponse response = mockMvc.perform(get("/api/recipes/5")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).header("X-Member-Id", "999"))
                .andReturn().getResponse();

        // then
        assertThat(response.getStatus()).isEqualTo(200);
        verify(recipeService).getRecipe(17L, 5L, null);
    }

    @Test
    @DisplayName("레시피 찜도 토큰의 회원 ID로 처리한다")
    void usesPrincipalForBookmarks() throws Exception {
        // given
        String token = tokenProvider.createMemberTokens(17L).accessToken();

        // when
        MockHttpServletResponse response = mockMvc.perform(post("/api/recipes/5/bookmarks")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).header("X-Member-Id", "999"))
                .andReturn().getResponse();

        // then
        assertThat(response.getStatus()).isEqualTo(201);
        verify(bookmarkService).addBookmark(17L, 5L);
    }

    @Test
    @DisplayName("공개 서비스 지역 확인은 잘못된 JWT 헤더가 있어도 JWT 검사를 하지 않는다")
    void permitsPublicRequestWithStaleToken() throws Exception {
        // given
        String path = "/api/service-area/check";

        // when
        MockHttpServletResponse response = mockMvc.perform(post(path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid")).andReturn().getResponse();

        // then
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("웹훅은 JWT 검사와 body 소비 없이 원문을 컨트롤러에 전달한다")
    void preservesWebhookBody() throws Exception {
        // given
        String body = "{\"event\":\"payment.paid\"}";

        // when
        MockHttpServletResponse response = mockMvc.perform(post("/api/webhooks/auth-test")
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid").contentType("application/json").content(body))
                .andReturn().getResponse();

        // then
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).isEqualTo(body);
    }

    @Test
    @DisplayName("허용한 Origin의 preflight는 인증 없이 통과하고 Idempotency-Key 헤더를 허용한다")
    void permitsConfiguredCorsPreflight() throws Exception {
        // given
        String origin = "http://localhost:3000";

        // when
        MockHttpServletResponse response = mockMvc.perform(options("/api/auth-test/member")
                .header(HttpHeaders.ORIGIN, origin).header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization,Content-Type,Idempotency-Key"))
                .andReturn().getResponse();

        // then
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isEqualTo(origin);
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS)).isEqualTo("true");
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS)).contains("Idempotency-Key");
    }

    @Test
    @DisplayName("등록하지 않은 Origin의 preflight는 거절한다")
    void rejectsUnconfiguredOrigin() throws Exception {
        // given
        String origin = "https://untrusted.example";

        // when
        MockHttpServletResponse response = mockMvc.perform(options("/api/auth-test/member")
                .header(HttpHeaders.ORIGIN, origin).header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andReturn().getResponse();

        // then
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isNull();
    }

    private String adminToken(String role, Long martId) {
        HashMap<String, Object> claims = new HashMap<>(Map.of(
                "type", "ADMIN", "adminId", 17L, "role", role));
        if (martId != null) {
            claims.put("martId", martId);
        }
        return signedToken(claims, Instant.now().plusSeconds(120));
    }

    private String signedToken(Map<String, Object> claims, Instant expiration) {
        return Jwts.builder().subject("17").id(UUID.randomUUID().toString())
                .issuedAt(Date.from(Instant.now().minusSeconds(60))).expiration(Date.from(expiration))
                .claim("tokenUse", "ACCESS").claims(claims).signWith(jwtSigningKey).compact();
    }

    private void assertError(MockHttpServletResponse response, int status, String code) throws Exception {
        assertThat(response.getStatus()).isEqualTo(status);
        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(body.path("success").asBoolean()).isFalse();
        assertThat(body.path("code").asText()).isEqualTo(code);
        assertThat(body.path("message").asText()).isNotBlank();
        assertThat(body.path("errors").isArray()).isTrue();
        assertThat(body.path("timestamp").asText()).endsWith("+09:00");
    }

    @RestController
    static class ProbeController {
        @GetMapping("/api/auth-test/member")
        ApiResponse<List<Long>> member(@LoginMember Long first, @LoginMember Long second) {
            return ApiResponse.success(List.of(first, second));
        }

        @GetMapping("/api/admin/auth-test/id")
        ApiResponse<Long> admin(@LoginAdmin Long adminId) {
            return ApiResponse.success(adminId);
        }

        @PreAuthorize("hasRole('SUPER_ADMIN')")
        @GetMapping("/api/admin/auth-test/super")
        ApiResponse<Void> superAdmin() {
            return ApiResponse.success();
        }

        @PostMapping("/api/service-area/check")
        ApiResponse<Void> publicCheck() {
            return ApiResponse.success();
        }

        @PostMapping("/api/webhooks/auth-test")
        String webhook(@RequestBody String body) {
            return body;
        }
    }
}
