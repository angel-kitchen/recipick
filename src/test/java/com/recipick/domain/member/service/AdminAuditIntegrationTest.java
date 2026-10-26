package com.recipick.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.recipick.IntegrationTestSupport;
import com.recipick.domain.member.entity.Admin;
import com.recipick.domain.member.entity.AdminAuditLog;
import com.recipick.domain.member.entity.Member;
import com.recipick.domain.member.entity.enums.AdminRole;
import com.recipick.domain.member.repository.AdminAuditLogRepository;
import com.recipick.domain.member.repository.AdminRepository;
import com.recipick.domain.member.repository.MemberRepository;
import com.recipick.domain.order.entity.enums.MartStatus;
import com.recipick.global.audit.AdminAuditAction;
import com.recipick.global.audit.AdminAuditChange;
import com.recipick.global.audit.AdminAuditCommand;
import com.recipick.global.audit.AdminAuditField;
import com.recipick.global.audit.AdminAuditRecorder;
import com.recipick.global.exception.AuthErrorCode;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.security.AuthPrincipal;
import com.recipick.global.security.SubjectType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class AdminAuditIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private AdminAuditRecorder recorder;
    @Autowired
    private AdminAuditLogRepository audits;
    @Autowired
    private AdminRepository admins;
    @Autowired
    private MemberRepository members;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transaction;
    private Admin superAdmin;
    private Admin martAdmin;
    private Member member;

    @BeforeEach
    void setUp() {
        transaction = new TransactionTemplate(transactionManager);
        superAdmin = admins.saveAndFlush(Admin.builder().loginId(UUID.randomUUID().toString())
                .name("감사 검증 슈퍼 관리자").passwordHash("hash").role(AdminRole.SUPER_ADMIN).active(true).build());
        martAdmin = admins.saveAndFlush(Admin.builder().loginId(UUID.randomUUID().toString())
                .name("감사 검증 마트 관리자").passwordHash("hash").role(AdminRole.MART_ADMIN)
                .martId(1L).active(true).build());
        member = members.saveAndFlush(Member.builder().email(UUID.randomUUID() + "@audit.test")
                .phone(UUID.randomUUID().toString().substring(0, 20)).name("감사 롤백 검증").passwordHash("hash").build());
        authenticate(superAdmin);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.7");
        request.addHeader("X-Forwarded-For", "203.0.113.1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
        jdbc.update("DELETE FROM admin_audit_log WHERE admin_id IN (?, ?)", superAdmin.getId(), martAdmin.getId());
        members.deleteById(member.getId());
        admins.deleteAllById(List.of(superAdmin.getId(), martAdmin.getId()));
    }

    @Test
    @DisplayName("업무 변경과 감사 기록을 함께 커밋하고 JSON·관리자·IP·생성 시각을 저장한다")
    void commitsBusinessAndAuditTogether() {
        // given
        AdminAuditCommand command = new AdminAuditCommand(AdminAuditAction.ORDER_ACCEPT, 10L, 1L,
                Map.of(AdminAuditField.STATUS, new AdminAuditChange(MartStatus.RECEIVED, MartStatus.PREPARING)));

        // when
        transaction.executeWithoutResult(status -> {
            members.findById(member.getId()).orElseThrow().agreeToLocation(LocalDateTime.now());
            recorder.record(command);
        });
        AdminAuditLog log = ownLog();

        // then
        assertThat(members.findById(member.getId()).orElseThrow().getLocationAgreedAt()).isNotNull();
        assertThat(log.getAdmin().getId()).isEqualTo(superAdmin.getId());
        assertThat(log.getAction()).isEqualTo(AdminAuditAction.ORDER_ACCEPT);
        assertThat(log.getTargetId()).isEqualTo(10L);
        assertThat(log.getMartId()).isEqualTo(1L);
        assertThat(log.getCreatedAt()).isNotNull();
        assertThat(log.getIpAddress()).isEqualTo("198.51.100.7");
        assertThat(log.getDetails().get("status")).containsEntry("before", "RECEIVED")
                .containsEntry("after", "PREPARING");
        assertThatThrownBy(() -> log.getDetails().get("status").put("after", "OTHER"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("감사 저장 뒤 후속 업무가 실패하면 감사 기록과 업무 변경을 모두 롤백한다")
    void rollsBackAuditWhenBusinessFails() {
        // given
        AdminAuditCommand command = listCommand();

        // when
        var failure = assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            members.findById(member.getId()).orElseThrow().agreeToLocation(LocalDateTime.now());
            recorder.record(command);
            throw new IllegalStateException("후속 업무 실패 검증");
        }));

        // then
        failure.isInstanceOf(IllegalStateException.class);
        assertThat(countOwnLogs()).isZero();
        assertThat(members.findById(member.getId()).orElseThrow().getLocationAgreedAt()).isNull();
    }

    @Test
    @DisplayName("실제 감사 INSERT가 실패하면 먼저 수행한 업무 변경도 롤백한다")
    void rollsBackBusinessWhenAuditInsertFails() {
        // given
        AdminAuditCommand command = new AdminAuditCommand(
                AdminAuditAction.ORDER_ACCEPT, 10L, Long.MAX_VALUE, Map.of());

        // when
        var failure = assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            members.findById(member.getId()).orElseThrow().agreeToLocation(LocalDateTime.now());
            recorder.record(command);
        }));

        // then
        failure.isInstanceOf(DataAccessException.class);
        assertThat(countOwnLogs()).isZero();
        assertThat(members.findById(member.getId()).orElseThrow().getLocationAgreedAt()).isNull();
    }

    @Test
    @DisplayName("바깥 트랜잭션이 없거나 읽기 전용이면 독립 커밋으로 우회하지 않고 실패한다")
    void requiresWritableOuterTransaction() {
        // given
        TransactionTemplate readOnly = new TransactionTemplate(transactionManager);
        readOnly.setReadOnly(true);

        // when
        var missing = assertThatThrownBy(() -> recorder.record(listCommand()));
        var readonly = assertThatThrownBy(() ->
                readOnly.executeWithoutResult(status -> recorder.record(listCommand())));

        // then
        missing.isInstanceOf(IllegalTransactionStateException.class);
        readonly.isInstanceOf(IllegalStateException.class);
        assertThat(countOwnLogs()).isZero();
    }

    @Test
    @DisplayName("목록 조회는 대상 ID·마트·details 없이 요청마다 별도 기록한다")
    void recordsRepeatedPersonalDataReads() {
        // given
        AdminAuditCommand command = listCommand();

        // when
        transaction.executeWithoutResult(status -> {
            recorder.record(command);
            recorder.record(command);
        });
        AdminAuditLog log = ownLog();

        // then
        assertThat(countOwnLogs()).isEqualTo(2);
        assertThat(log.getTargetId()).isNull();
        assertThat(log.getMartId()).isNull();
        assertThat(log.getDetails()).isNull();
    }

    @Test
    @DisplayName("마트 관리자는 자기 마트 작업만 기록하고 슈퍼 관리자 작업은 기록하지 못한다")
    void enforcesAdminRoleAndMartScope() {
        // given
        authenticate(martAdmin);

        // when
        transaction.executeWithoutResult(status -> recorder.record(
                new AdminAuditCommand(AdminAuditAction.PRODUCT_CREATE, 10L, 1L, null)));
        var otherMart = assertThatThrownBy(() -> transaction.executeWithoutResult(status -> recorder.record(
                new AdminAuditCommand(AdminAuditAction.PRODUCT_CREATE, 10L, 2L, null))));
        var superOnly = assertThatThrownBy(() ->
                transaction.executeWithoutResult(status -> recorder.record(listCommand())));

        // then
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_audit_log WHERE admin_id = ?",
                Long.class, martAdmin.getId())).isEqualTo(1L);
        otherMart.isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.MART_ACCESS_DENIED));
        superOnly.isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.SUPER_ADMIN_ONLY));
    }

    @Test
    @DisplayName("비활성 관리자나 저장된 권한과 다른 기존 토큰은 기록할 수 없다")
    void checksStoredAdminStatusAndRole() {
        // given
        jdbc.update("UPDATE admin SET active = false WHERE id = ?", superAdmin.getId());

        // when
        var inactive = assertThatThrownBy(() ->
                transaction.executeWithoutResult(status -> recorder.record(listCommand())));
        jdbc.update("UPDATE admin SET active = true, role = 'MART_ADMIN', mart_id = 1 WHERE id = ?",
                superAdmin.getId());
        var stale = assertThatThrownBy(() ->
                transaction.executeWithoutResult(status -> recorder.record(listCommand())));

        // then
        inactive.isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.INACTIVE_ADMIN));
        stale.isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.ACCESS_DENIED));
        assertThat(countOwnLogs()).isZero();
    }

    @Test
    @DisplayName("관리자 인증이 없거나 일반 회원이면 감사 기록을 저장하지 않는다")
    void rejectsNonAdminActor() {
        // given
        SecurityContextHolder.clearContext();

        // when
        var anonymous = assertThatThrownBy(() ->
                transaction.executeWithoutResult(status -> recorder.record(listCommand())));
        AuthPrincipal principal = new AuthPrincipal(SubjectType.MEMBER, member.getId(), null, null);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.authorities()));
        var ordinary = assertThatThrownBy(() ->
                transaction.executeWithoutResult(status -> recorder.record(listCommand())));

        // then
        anonymous.isInstanceOf(BusinessException.class);
        ordinary.isInstanceOf(BusinessException.class);
        assertThat(countOwnLogs()).isZero();
    }

    @Test
    @DisplayName("실제 테이블에는 두 외래키와 JSON 컬럼만 만들고 updated_at과 MySQL enum은 만들지 않는다")
    void matchesAuditSchema() {
        // given
        String schema = jdbc.queryForObject("SELECT DATABASE()", String.class);

        // when
        List<String> columns = jdbc.queryForList("SELECT COLUMN_NAME FROM information_schema.COLUMNS "
                + "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = 'admin_audit_log'", String.class, schema);
        List<String> foreignKeys = jdbc.queryForList("SELECT COLUMN_NAME FROM information_schema.KEY_COLUMN_USAGE "
                + "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = 'admin_audit_log' AND REFERENCED_TABLE_NAME IS NOT NULL",
                String.class, schema);
        List<String> types = jdbc.queryForList("SELECT DATA_TYPE FROM information_schema.COLUMNS "
                + "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = 'admin_audit_log' "
                + "AND COLUMN_NAME IN ('action','target_type')",
                String.class, schema);

        // then
        assertThat(columns).containsExactlyInAnyOrder("id", "admin_id", "action", "target_type", "target_id",
                "mart_id", "details", "ip_address", "created_at");
        assertThat(foreignKeys).containsExactlyInAnyOrder("admin_id", "mart_id");
        assertThat(types).containsOnly("varchar");
    }

    private AdminAuditCommand listCommand() {
        return new AdminAuditCommand(AdminAuditAction.MEMBER_LIST_VIEW, null, null, null);
    }

    private long countOwnLogs() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM admin_audit_log WHERE admin_id = ?",
                Long.class, superAdmin.getId());
    }

    private AdminAuditLog ownLog() {
        Long id = jdbc.queryForObject("SELECT MAX(id) FROM admin_audit_log WHERE admin_id = ?",
                Long.class, superAdmin.getId());
        return audits.findById(id).orElseThrow();
    }

    private void authenticate(Admin admin) {
        AuthPrincipal principal = new AuthPrincipal(SubjectType.ADMIN, admin.getId(),
                admin.getRole().name(), admin.getMartId());
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.authorities()));
    }
}
