package com.recipick.domain.member.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import com.recipick.domain.member.dto.request.MemberWithdrawRequest;
import com.recipick.domain.member.entity.Member;
import com.recipick.domain.member.exception.MemberErrorCode;
import com.recipick.domain.member.service.MemberService;
import com.recipick.domain.member.service.MembershipService;
import com.recipick.domain.member.service.WithdrawalPaymentMethodPreparation;
import com.recipick.domain.member.service.WithdrawalPaymentMethodPreparer;
import com.recipick.domain.member.service.WithdrawalSettlementChecker;
import com.recipick.domain.order.service.OrderService;
import com.recipick.global.exception.BusinessException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.security.crypto.password.PasswordEncoder;

class MemberWithdrawalPreparationProcessorTest {
    MemberService members = mock(MemberService.class);
    OrderService orders = mock(OrderService.class);
    MembershipService memberships = mock(MembershipService.class);
    WithdrawalSettlementChecker checker = mock(WithdrawalSettlementChecker.class);
    WithdrawalPaymentMethodPreparer methods = mock(WithdrawalPaymentMethodPreparer.class);
    PasswordEncoder encoder = mock(PasswordEncoder.class);
    Clock clock = Clock.systemUTC();
    MemberWithdrawRequest request = new MemberWithdrawRequest("password", 0L);
    MemberWithdrawalPreparationProcessor processor;

    @BeforeEach
    void setUp() {
        var factory = new StaticListableBeanFactory();
        factory.addBean("checker", checker);
        processor = new MemberWithdrawalPreparationProcessor(members, orders, memberships,
                factory.getBeanProvider(WithdrawalSettlementChecker.class), methods, guard(), clock);
        given(members.getMemberForUpdate(1L)).willReturn(Member.builder().passwordHash("hash").build());
        given(encoder.matches("password", "hash")).willReturn(true);
        given(checker.supportedScopes()).willReturn(EnumSet.allOf(WithdrawalSettlementChecker.Scope.class));
    }

    @Test
    @DisplayName("첫 회원 잠금과 기본 제한 검사를 마친 뒤에만 카드 정리를 접수한다")
    void locksFirstAndPreparesAfterChecks() {
        // given
        var expected = WithdrawalPaymentMethodPreparation.ready();
        given(methods.prepare(1L)).willReturn(expected);
        // when
        assertThat(processor.prepare(1L, request)).isEqualTo(expected);
        // then
        var order = inOrder(members, encoder, orders, memberships, checker, methods);
        order.verify(members).getMemberForUpdate(1L);
        order.verify(encoder).matches("password", "hash");
        order.verify(orders).existsInProgressOrder(1L);
        order.verify(memberships).existsBlocking(eq(1L), any(LocalDateTime.class));
        order.verify(checker).supportedScopes();
        order.verify(methods).prepare(1L);
        org.mockito.Mockito.verify(checker, org.mockito.Mockito.never()).requireSettled(any(), any());
    }

    @Test
    @DisplayName("비밀번호가 틀리면 카드 정리보다 먼저 거절한다")
    void rejectsWrongPasswordBeforePreparation() {
        // given
        given(encoder.matches("password", "hash")).willReturn(false);
        // when, then
        assertError(MemberErrorCode.INVALID_CURRENT_PASSWORD);
        verifyNoInteractions(orders, memberships, checker, methods);
    }

    @Test
    @DisplayName("진행 주문이 있으면 카드 정리를 접수하지 않는다")
    void rejectsOrder() {
        // given
        given(orders.existsInProgressOrder(1L)).willReturn(true);
        // when, then
        assertError(MemberErrorCode.WITHDRAW_BLOCKED_ORDER);
        verifyNoInteractions(memberships, checker, methods);
    }

    @Test
    @DisplayName("활성·대기 이용권이 있으면 카드 정리를 접수하지 않는다")
    void rejectsMembership() {
        // given
        given(memberships.existsBlocking(eq(1L), any())).willReturn(true);
        // when, then
        assertError(MemberErrorCode.WITHDRAW_BLOCKED_MEMBERSHIP);
        verifyNoInteractions(checker, methods);
    }

    @ParameterizedTest
    @EnumSource(WithdrawalSettlementChecker.Scope.class)
    @DisplayName("정산 영역 하나라도 없으면 카드 정리 접수 전에 차단한다")
    void rejectsMissingScope(WithdrawalSettlementChecker.Scope missing) {
        // given
        var scopes = EnumSet.allOf(WithdrawalSettlementChecker.Scope.class);
        scopes.remove(missing);
        given(checker.supportedScopes()).willReturn(scopes);
        // when, then
        assertError(MemberErrorCode.WITHDRAW_SETTLEMENT_NOT_READY);
        verifyNoInteractions(methods);
    }

    void assertError(MemberErrorCode expected) {
        assertThatThrownBy(() -> processor.prepare(1L, request)).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(expected));
    }

    private com.recipick.domain.member.service.SensitivePasswordGuard guard() {
        var attempts = mock(com.recipick.infra.redis.RedisLoginAttemptStore.class);
        given(attempts.clearIfUnlocked(any(), any())).willReturn(true);
        return new com.recipick.domain.member.service.SensitivePasswordGuard(attempts, encoder);
    }
}
