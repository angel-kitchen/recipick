package com.recipick.domain.member.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipick.IntegrationTestSupport;
import com.recipick.domain.member.entity.Member;
import com.recipick.domain.member.entity.Membership;
import com.recipick.domain.member.entity.enums.MembershipSource;
import com.recipick.domain.member.entity.enums.MembershipType;
import com.recipick.domain.member.repository.MemberRepository;
import com.recipick.domain.member.repository.MembershipRepository;
import com.recipick.global.security.AuthPrincipal;
import com.recipick.global.security.JwtTokenProvider;
import com.recipick.global.security.SubjectType;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class MemberAdminIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private MemberRepository members;
    @Autowired
    private MembershipRepository memberships;
    @Autowired
    private JwtTokenProvider tokens;
    @Autowired
    private Clock clock;

    private String prefix;
    private String superToken;
    private LocalDateTime now;
    private List<Member> fixtures;

    @BeforeEach
    void setUp() {
        prefix = UUID.randomUUID().toString().substring(0, 12);
        superToken = tokens.createTokens(new AuthPrincipal(SubjectType.ADMIN, 1L, "SUPER_ADMIN", null)).accessToken();
        now = LocalDateTime.now(clock).withNano(0);
        fixtures = new ArrayList<>();
        List<String> names = List.of("plain", "trial", "paid", "grant", "expired", "terminated", "reserved", "future");
        for (String suffix : names) {
            fixtures.add(members.saveAndFlush(Member.builder().email(prefix + "-" + suffix + "@test.com")
                    .phone(UUID.randomUUID().toString().substring(0, 20)).name(suffix).passwordHash("hash").build()));
        }
        saveMembership(1, MembershipType.TRIAL, MembershipSource.TRIAL, -2, 5);
        saveMembership(2, MembershipType.PAID, MembershipSource.SUBSCRIBE, -3, 27);
        saveMembership(3, MembershipType.PAID, MembershipSource.ADMIN_GRANT, -3, 27);
        saveMembership(4, MembershipType.TRIAL, MembershipSource.TRIAL, -8, -1);
        Membership terminated = saveMembership(5, MembershipType.PAID, MembershipSource.SUBSCRIBE, -3, 27);
        terminated.terminateAfterRefund(now.minusHours(1));
        Membership reserved = saveMembership(6, MembershipType.PAID, MembershipSource.SUBSCRIBE, -3, 27);
        reserved.reserveCancellation(now.minusHours(1));
        saveMembership(7, MembershipType.PAID, MembershipSource.SUBSCRIBE, 1, 31);
        memberships.flush();
    }

    @Test
    @DisplayName("회원 목록은 기본 페이지 정보와 필요한 필드만 반환한다")
    void returnsPageWithoutSensitiveFields() throws Exception {
        // given
        String[] query = {};

        // when
        MockHttpServletResponse response = request(superToken, query);

        // then
        assertThat(response.getStatus()).isEqualTo(200);
        JsonNode data = data(response);
        assertThat(data.path("totalElements").asInt()).isEqualTo(8);
        assertThat(data.path("page").asInt()).isZero();
        assertThat(data.path("size").asInt()).isEqualTo(20);
        assertThat(data.path("totalPages").asInt()).isEqualTo(1);
        for (JsonNode row : data.path("content")) {
            assertThat(row.has("phone")).isFalse();
            assertThat(row.has("passwordHash")).isFalse();
            assertThat(row.has("address")).isFalse();
            assertThat(row.path("createdAt").asText()).endsWith("+09:00");
        }
    }

    @ParameterizedTest
    @CsvSource({"true,trial,paid,grant,reserved", "false,plain,expired,terminated,future"})
    @DisplayName("활성 필터는 기간·즉시 종료를 반영하고 해지 예약은 활성으로 유지한다")
    void filtersMembershipActivity(String active, String first, String second, String third, String fourth)
            throws Exception {
        // given
        List<String> expected = List.of(first, second, third, fourth);

        // when
        MockHttpServletResponse response = request(superToken, "membershipActive", active);

        // then
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(data(response).path("totalElements").asInt()).isEqualTo(4);
        List<String> names = new ArrayList<>();
        for (JsonNode row : data(response).path("content")) {
            names.add(row.path("name").asText());
            assertThat(row.path("membershipActive").asBoolean()).isEqualTo(Boolean.parseBoolean(active));
            if (active.equals("false")) {
                assertThat(row.path("membershipType").isNull()).isTrue();
                assertThat(row.path("membershipEndAt").isNull()).isTrue();
            }
        }
        assertThat(names).containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    @DisplayName("체험 사용 이력은 만료된 체험도 포함하고 관리자 지급 이력과 구분한다")
    void distinguishesTrialHistoryFromAdminGrant() throws Exception {
        // given
        String[] query = {};

        // when
        MockHttpServletResponse response = request(superToken, query);

        // then
        for (JsonNode row : data(response).path("content")) {
            String name = row.path("name").asText();
            assertThat(row.path("trialUsed").asBoolean()).isEqualTo(List.of("trial", "expired").contains(name));
            if (name.equals("grant")) {
                assertThat(row.path("membershipType").asText()).isEqualTo("PAID");
            }
        }
    }

    @ParameterizedTest
    @CsvSource({"memberId,asc", "memberId,desc", "email,asc", "email,desc", "name,asc", "name,desc"})
    @DisplayName("허용된 정렬과 페이지를 적용해 중복 없이 회원을 조회한다")
    void sortsAndPaginates(String property, String direction) throws Exception {
        // given
        List<Member> sorted = new ArrayList<>(fixtures);
        java.util.Comparator<Member> comparator = switch (property) {
            case "memberId" -> java.util.Comparator.comparing(Member::getId);
            case "email" -> java.util.Comparator.comparing(Member::getEmail);
            default -> java.util.Comparator.comparing(Member::getName);
        };
        sorted.sort(direction.equals("asc") ? comparator : comparator.reversed());

        // when
        MockHttpServletResponse response = request(superToken, "sort", property + "," + direction,
                "page", "1", "size", "3");

        // then
        assertThat(response.getStatus()).isEqualTo(200);
        JsonNode data = data(response);
        assertThat(data.path("totalElements").asInt()).isEqualTo(8);
        assertThat(data.path("totalPages").asInt()).isEqualTo(3);
        for (int i = 0; i < 3; i++) {
            assertThat(data.path("content").get(i).path("memberId").asLong()).isEqualTo(sorted.get(i + 3).getId());
        }
    }

    @Test
    @DisplayName("이메일 부분 검색은 대소문자·주변 공백을 처리하고 LIKE 특수문자를 그대로 검색한다")
    void searchesEmailLiterally() throws Exception {
        // given
        String email = "  " + prefix.toUpperCase() + "-PAID@TEST.COM  ";

        // when
        MockHttpServletResponse found = request(superToken, "email", email);
        MockHttpServletResponse percent = request(superToken, "email", prefix + "%");
        MockHttpServletResponse underscore = request(superToken, "email", prefix + "_");

        // then
        assertThat(data(found).path("totalElements").asInt()).isEqualTo(1);
        assertThat(data(found).path("content").get(0).path("name").asText()).isEqualTo("paid");
        assertThat(data(percent).path("totalElements").asInt()).isZero();
        assertThat(data(underscore).path("totalElements").asInt()).isZero();
    }

    @Test
    @DisplayName("여러 이용권 이력이 있어도 회원과 전체 개수는 중복되지 않는다")
    void avoidsDuplicatesFromMembershipHistory() throws Exception {
        // given
        saveMembership(2, MembershipType.TRIAL, MembershipSource.TRIAL, -40, -33);

        // when
        MockHttpServletResponse response = request(superToken, "membershipActive", "true");

        // then
        assertThat(data(response).path("totalElements").asInt()).isEqualTo(4);
        assertThat(data(response).path("content").size()).isEqualTo(4);
        for (JsonNode row : data(response).path("content")) {
            if (row.path("name").asText().equals("paid")) {
                assertThat(row.path("trialUsed").asBoolean()).isTrue();
            }
        }
    }

    @Test
    @DisplayName("빈 결과와 마지막 페이지 밖 조회도 올바른 페이지 정보를 반환한다")
    void returnsEmptyPages() throws Exception {
        // given
        String missing = prefix + "-missing";

        // when
        MockHttpServletResponse none = request(superToken, "email", missing);
        MockHttpServletResponse beyond = request(superToken, "page", "99", "size", "3");

        // then
        assertThat(data(none).path("content").isEmpty()).isTrue();
        assertThat(data(none).path("totalElements").asInt()).isZero();
        assertThat(data(beyond).path("content").isEmpty()).isTrue();
        assertThat(data(beyond).path("totalElements").asInt()).isEqualTo(8);
    }

    @ParameterizedTest
    @CsvSource({"page,-1,", "size,0,", "size,101,", "sort,passwordHash,asc", "sort,createdAt,", "sort,id,asc"})
    @DisplayName("잘못된 페이지·크기·정렬 요청은 공통 입력 오류로 거절한다")
    void rejectsInvalidQuery(String key, String value, String remainder) throws Exception {
        // given
        String input = remainder == null ? value : value + "," + remainder;

        // when
        MockHttpServletResponse response = request(superToken, key, input);

        // then
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(mapper.readTree(response.getContentAsString()).path("code").asText()).isEqualTo("COMMON-001");
    }

    @Test
    @DisplayName("비로그인·일반 회원·마트 관리자는 회원 목록을 조회할 수 없다")
    void restrictsAccessToSuperAdmin() throws Exception {
        // given
        String member = tokens.createMemberTokens(fixtures.get(0).getId()).accessToken();
        String mart = tokens.createTokens(new AuthPrincipal(SubjectType.ADMIN, 1L, "MART_ADMIN", 1L)).accessToken();

        // when
        MockHttpServletResponse anonymous = request(null);
        MockHttpServletResponse memberResponse = request(member);
        MockHttpServletResponse martResponse = request(mart);

        // then
        assertThat(anonymous.getStatus()).isEqualTo(401);
        assertThat(memberResponse.getStatus()).isEqualTo(403);
        assertThat(martResponse.getStatus()).isEqualTo(403);
        assertThat(mapper.readTree(martResponse.getContentAsString()).path("code").asText()).isEqualTo("AUTH-006");
    }

    private Membership saveMembership(int index, MembershipType type, MembershipSource source, int start, int end) {
        return memberships.saveAndFlush(Membership.builder().member(fixtures.get(index)).type(type).source(source)
                .startAt(now.plusDays(start)).endAt(now.plusDays(end))
                .autoRenew(source != MembershipSource.ADMIN_GRANT).build());
    }

    private MockHttpServletResponse request(String token, String... parameters) throws Exception {
        var request = get("/api/admin/members");
        boolean emailProvided = false;
        for (int i = 0; i < parameters.length; i += 2) {
            request.param(parameters[i], parameters[i + 1]);
            emailProvided |= parameters[i].equals("email");
        }
        if (!emailProvided) {
            request.param("email", prefix);
        }
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return mvc.perform(request).andReturn().getResponse();
    }

    private JsonNode data(MockHttpServletResponse response) throws Exception {
        return mapper.readTree(response.getContentAsString()).path("data");
    }
}
