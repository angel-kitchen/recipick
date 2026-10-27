package com.recipick.domain.payment.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.recipick.IntegrationTestSupport;
import com.recipick.domain.member.entity.Admin;
import com.recipick.domain.member.entity.enums.AdminRole;
import com.recipick.domain.member.repository.AdminRepository;
import com.recipick.global.security.AuthPrincipal;
import com.recipick.global.security.JwtTokenProvider;
import com.recipick.global.security.SubjectType;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

// 클래스에 @Transactional을 두지 않는다. 테스트가 트랜잭션을 열어 주지 않아도 Facade가 스스로 쓰기 트랜잭션을 열어
// 감사 기록을 커밋하는지 확인한다 — Facade의 @Transactional이 빠지거나 readOnly가 되면 record()가 실패해 500이 된다
class PaymentAdminTransactionBoundaryTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtTokenProvider tokens;
    @Autowired
    private AdminRepository adminRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long superAdminId;

    @BeforeEach
    void setUp() {
        superAdminId = adminRepository.saveAndFlush(Admin.builder()
            .loginId(UUID.randomUUID().toString())
            .name("결제 조회 트랜잭션 검증 관리자")
            .passwordHash("hash")
            .role(AdminRole.SUPER_ADMIN)
            .active(true)
            .build()).getId();
    }

    // 트랜잭션 없이 커밋된 행이라 직접 지운다. 감사 기록이 관리자를 참조하므로 감사 기록부터 지운다
    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM admin_audit_log WHERE admin_id = ?", superAdminId);
        adminRepository.deleteById(superAdminId);
    }

    @Test
    @DisplayName("테스트 트랜잭션이 없어도 Facade가 직접 쓰기 트랜잭션을 열어 조회 감사 기록을 커밋한다")
    void facadeOpensWriteTransaction() throws Exception {
        // given
        String token = tokens.createTokens(
            new AuthPrincipal(SubjectType.ADMIN, superAdminId, "SUPER_ADMIN", null)).accessToken();
        // 주문이 없는 회원으로 조회해 다른 테스트가 남긴 결제 데이터와 무관하게 만든다
        String url = "/api/admin/payments?memberId=" + Long.MAX_VALUE;

        // when
        MockHttpServletResponse response = mockMvc.perform(get(url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andReturn().getResponse();

        // then
        assertThat(response.getStatus()).isEqualTo(200);
        List<String> actions = jdbcTemplate.queryForList(
            "SELECT action FROM admin_audit_log WHERE admin_id = ?", String.class, superAdminId);
        assertThat(actions).containsExactly("PAYMENT_LIST_VIEW");
    }
}
