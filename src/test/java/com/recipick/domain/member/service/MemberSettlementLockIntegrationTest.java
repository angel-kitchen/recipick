package com.recipick.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.recipick.IntegrationTestSupport;
import com.recipick.domain.member.entity.Member;
import com.recipick.domain.member.exception.MemberErrorCode;
import com.recipick.domain.member.repository.MemberRepository;
import com.recipick.global.exception.BusinessException;
import jakarta.persistence.EntityManager;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Transactional
class MemberSettlementLockIntegrationTest extends IntegrationTestSupport {
    @Autowired MemberService service;
    @Autowired MemberRepository members;
    @Autowired EntityManager entityManager;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired DataSource dataSource;

    private Member member() {
        String suffix = UUID.randomUUID().toString().substring(0, 20);
        return members.saveAndFlush(Member.builder().email(suffix + "@settle.test")
                .phone(suffix).name("정산 회원").passwordHash("hash").build());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("정산 잠금은 활성·탈퇴 회원 모두 허용하고 DTO의 탈퇴 여부를 반환한다")
    void returnsMemberStateIncludingWithdrawn(boolean withdrawn) {
        // given
        Member member = member();
        if (withdrawn) {
            member.withdraw(LocalDateTime.of(2026, 11, 2, 12, 0));
            members.flush();
        }
        Long id = member.getId();
        entityManager.clear();
        // when
        var result = service.lockMemberForSettlement(id);
        // then
        assertThat(result.memberId()).isEqualTo(id);
        assertThat(result.withdrawn()).isEqualTo(withdrawn);
        if (withdrawn) {
            assertThatThrownBy(() -> service.lockActiveMember(id)).isInstanceOfSatisfying(BusinessException.class,
                    error -> assertThat(error.getErrorCode()).isEqualTo(MemberErrorCode.MEMBER_NOT_FOUND));
        }
    }

    @Test
    @DisplayName("존재하지 않는 회원의 정산 잠금은 MEMBER-001로 거절한다")
    void rejectsMissingMember() {
        // given, when, then
        assertThatThrownBy(() -> service.lockMemberForSettlement(-1L)).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(MemberErrorCode.MEMBER_NOT_FOUND));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("정산 잠금은 바깥 트랜잭션이 없으면 실행하지 않는다")
    void requiresExistingTransaction() {
        // given, when, then
        assertThatThrownBy(() -> service.lockMemberForSettlement(1L)).isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    @Transactional(readOnly = true)
    @DisplayName("정산 잠금은 읽기 전용 트랜잭션에서 DB 조회 전에 거절한다")
    void rejectsReadOnlyTransaction() {
        // given, when, then
        assertThatThrownBy(() -> service.lockMemberForSettlement(1L)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("정산 잠금 메서드가 반환한 뒤에도 바깥 트랜잭션 종료까지 회원 PK 잠금을 유지한다")
    void holdsPkLockUntilOuterTransactionEnds() throws Exception {
        // given
        Member member = member();
        var pool = Executors.newSingleThreadExecutor();
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                service.lockMemberForSettlement(member.getId());
                // when
                var waiter = pool.submit(() -> {
                    try (var connection = dataSource.getConnection()) {
                        connection.setAutoCommit(false);
                        int previousTimeout;
                        try (var setting = connection.createStatement();
                                var result = setting.executeQuery("SELECT @@SESSION.innodb_lock_wait_timeout")) {
                            result.next();
                            previousTimeout = result.getInt(1);
                        }
                        try (var setting = connection.createStatement()) {
                            setting.execute("SET SESSION innodb_lock_wait_timeout = 1");
                        }
                        try (var query = connection.prepareStatement("SELECT id FROM member WHERE id = ? FOR UPDATE")) {
                            query.setLong(1, member.getId());
                            try (var result = query.executeQuery()) {
                                return 0;
                            }
                        } catch (SQLException error) {
                            return error.getErrorCode();
                        } finally {
                            connection.rollback();
                            // 커넥션 풀의 다른 테스트에 잠금 대기 설정이 남지 않도록 복원한다.
                            try (var setting = connection.createStatement()) {
                                setting.execute("SET SESSION innodb_lock_wait_timeout = " + previousTimeout);
                            }
                        }
                    }
                });
                try {
                    // then: 별도 연결이 같은 PK 잠금을 얻지 못하고 MySQL 잠금 대기 시간 초과로 끝난다.
                    assertThat(waiter.get(10, TimeUnit.SECONDS)).isEqualTo(1205);
                } catch (Exception error) {
                    throw new IllegalStateException("정산 회원 잠금 검증 실패", error);
                }
            });
            new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                    assertThat(service.lockMemberForSettlement(member.getId()).memberId()).isEqualTo(member.getId()));
        } finally {
            pool.shutdownNow();
            pool.awaitTermination(5, TimeUnit.SECONDS);
            members.deleteById(member.getId());
        }
    }
}
