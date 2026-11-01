package com.recipick.domain.member.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.recipick.IntegrationTestSupport;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class MembershipGrantTransactionIntegrationTest extends IntegrationTestSupport {
    @Autowired MembershipGrantService grants;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("실제 지급은 Facade의 바깥 트랜잭션 없이 실행하지 않는다")
    void requiresExistingTransaction() {
        // given, when, then
        assertThatThrownBy(() -> grants.grant(null, null, LocalDateTime.of(2026, 1, 31, 0, 0)))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    @DisplayName("읽기 전용 트랜잭션에서는 실제 지급과 감사 기록을 시작하지 않는다")
    void rejectsReadOnlyTransaction() {
        // given
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setReadOnly(true);
        // when, then
        assertThatThrownBy(() -> tx.executeWithoutResult(status ->
                grants.grant(null, null, LocalDateTime.of(2026, 1, 31, 0, 0))))
                .isInstanceOf(IllegalStateException.class);
    }
}
