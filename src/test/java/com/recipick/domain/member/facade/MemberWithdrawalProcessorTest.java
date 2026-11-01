package com.recipick.domain.member.facade;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

import com.recipick.domain.member.dto.request.MemberWithdrawRequest;
import com.recipick.domain.member.entity.Member;
import com.recipick.domain.member.exception.MemberErrorCode;
import com.recipick.domain.member.service.MemberService;
import com.recipick.domain.member.service.MembershipService;
import com.recipick.domain.member.service.MemberWithdrawalService;
import com.recipick.domain.member.service.TrialBlockService;
import com.recipick.domain.member.service.WithdrawalSettlementChecker;
import com.recipick.domain.order.service.CartService;
import com.recipick.domain.order.service.OrderService;
import com.recipick.global.exception.BusinessException;
import java.time.Clock;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.security.crypto.password.PasswordEncoder;

class MemberWithdrawalProcessorTest {
    MemberService members = mock(MemberService.class);
    OrderService orders = mock(OrderService.class);
    CartService carts = mock(CartService.class);
    MembershipService memberships = mock(MembershipService.class);
    TrialBlockService blocks = mock(TrialBlockService.class);
    MemberWithdrawalService withdrawal = mock(MemberWithdrawalService.class);
    WithdrawalSettlementChecker checker = mock(WithdrawalSettlementChecker.class);
    PasswordEncoder encoder = mock(PasswordEncoder.class);
    Clock clock = Clock.systemUTC();

    private MemberWithdrawalProcessor processor() {
        return processor(checker);
    }

    private MemberWithdrawalProcessor processor(WithdrawalSettlementChecker... checkers) {
        var factory = new StaticListableBeanFactory();
        for (int i = 0; i < checkers.length; i++) {
            factory.addBean("checker" + i, checkers[i]);
        }
        return new MemberWithdrawalProcessor(members, orders, carts, memberships, blocks, withdrawal,
                factory.getBeanProvider(WithdrawalSettlementChecker.class), encoder, clock);
    }

    @Test
    @DisplayName("회원 잠금부터 시작하고 모든 조건 검사 이후 개인정보를 변경한다")
    void locksFirstAndMutatesLast() {
        // given
        Member member = Member.builder().passwordHash("encoded").build();
        given(members.getMemberForUpdate(1L)).willReturn(member);
        given(encoder.matches("password", "encoded")).willReturn(true);
        given(checker.supportedScopes()).willReturn(java.util.EnumSet.allOf(WithdrawalSettlementChecker.Scope.class));
        // when
        processor().withdraw(1L, new MemberWithdrawRequest("password", 0L));
        // then
        var order = inOrder(members, encoder, orders, memberships, checker, carts, blocks, withdrawal);
        order.verify(members).getMemberForUpdate(1L);
        order.verify(encoder).matches("password", "encoded");
        order.verify(orders).existsInProgressOrder(1L);
        order.verify(memberships).existsBlocking(eq(1L), any(LocalDateTime.class));
        order.verify(checker).requireSettled(1L, 0L);
        order.verify(carts).deleteAllByMemberId(1L);
        order.verify(blocks).recordWithdrawal(eq(1L), any(LocalDateTime.class));
        order.verify(withdrawal).withdraw(eq(member), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("진행 중 주문이 있으면 개인정보 변경과 체험 차단 기록을 하지 않는다")
    void refusesInProgressOrder() {
        // given
        Member member = Member.builder().passwordHash("encoded").build();
        given(members.getMemberForUpdate(1L)).willReturn(member);
        given(encoder.matches("password", "encoded")).willReturn(true);
        given(orders.existsInProgressOrder(1L)).willReturn(true);
        // when, then
        assertThatThrownBy(() -> processor().withdraw(1L, new MemberWithdrawRequest("password", 0L)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> org.assertj.core.api.Assertions.assertThat(e.getErrorCode())
                                .isEqualTo(MemberErrorCode.WITHDRAW_BLOCKED_ORDER));
        verifyNoInteractions(memberships, checker, carts, blocks, withdrawal);
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(WithdrawalSettlementChecker.Scope.class)
    @DisplayName("정산 영역이 하나라도 미연결이면 실제 정산 및 개인정보 변경을 시작하지 않는다")
    void refusesPartialIntegration(WithdrawalSettlementChecker.Scope missingScope) {
        // given
        Member member = Member.builder().passwordHash("encoded").build();
        given(members.getMemberForUpdate(1L)).willReturn(member);
        given(encoder.matches("password", "encoded")).willReturn(true);
        var scopes = java.util.EnumSet.allOf(WithdrawalSettlementChecker.Scope.class);
        scopes.remove(missingScope);
        given(checker.supportedScopes()).willReturn(scopes);
        // when, then
        assertThatThrownBy(() -> processor().withdraw(1L, new MemberWithdrawRequest("password", 0L)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> org.assertj.core.api.Assertions.assertThat(e.getErrorCode())
                                .isEqualTo(MemberErrorCode.WITHDRAW_SETTLEMENT_NOT_READY));
        verify(checker, never()).requireSettled(anyLong(), anyLong());
        verifyNoInteractions(carts, blocks, withdrawal);
    }

    @Test
    @DisplayName("영역 연결을 선언하지 않은 기존 구현체는 기본적으로 준비되지 않은 상태다")
    void defaultsToUnready() {
        // given
        WithdrawalSettlementChecker legacy = (memberId, balance) -> {};
        // when, then
        org.assertj.core.api.Assertions.assertThat(legacy.supportedScopes()).isEmpty();
    }

    private void allowMember() {
        given(members.getMemberForUpdate(1L)).willReturn(Member.builder().passwordHash("encoded").build());
        given(encoder.matches("password", "encoded")).willReturn(true);
    }

    @Test
    @DisplayName("여러 구현체의 범위를 합쳐 전체 연결을 확인하고 모든 정산 검사를 실행한다")
    void combinesScopesAndRunsAllCheckers() {
        // given
        allowMember();
        WithdrawalSettlementChecker other = mock(WithdrawalSettlementChecker.class);
        given(checker.supportedScopes()).willReturn(java.util.Set.of(
                WithdrawalSettlementChecker.Scope.PRODUCT_PAYMENT, WithdrawalSettlementChecker.Scope.POINTS));
        given(other.supportedScopes()).willReturn(java.util.Set.of(
                WithdrawalSettlementChecker.Scope.MEMBERSHIP_PAYMENT, WithdrawalSettlementChecker.Scope.AUTO_BILLING));
        // when
        processor(checker, other).withdraw(1L, new MemberWithdrawRequest("password", 0L));
        // then
        verify(checker).requireSettled(1L, 0L);
        verify(other).requireSettled(1L, 0L);
        verify(withdrawal).withdraw(any(Member.class), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("구현체가 없으면 정산 미연결로 거절한다")
    void refusesMissingCheckers() {
        // given
        allowMember();
        // when, then
        assertThatThrownBy(() -> processor(new WithdrawalSettlementChecker[0])
                .withdraw(1L, new MemberWithdrawRequest("password", 0L)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> org.assertj.core.api.Assertions.assertThat(e.getErrorCode())
                                .isEqualTo(MemberErrorCode.WITHDRAW_SETTLEMENT_NOT_READY));
        verifyNoInteractions(blocks, withdrawal);
    }

    @Test
    @DisplayName("구현체가 여러 개여도 합산 범위가 부족하면 모든 정산 검사를 시작하지 않는다")
    void refusesIncompleteCombinedScopes() {
        // given
        allowMember();
        WithdrawalSettlementChecker other = mock(WithdrawalSettlementChecker.class);
        given(checker.supportedScopes()).willReturn(java.util.Set.of(WithdrawalSettlementChecker.Scope.PRODUCT_PAYMENT));
        given(other.supportedScopes()).willReturn(null);
        // when, then
        assertThatThrownBy(() -> processor(checker, other).withdraw(1L, new MemberWithdrawRequest("password", 0L)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> org.assertj.core.api.Assertions.assertThat(e.getErrorCode())
                                .isEqualTo(MemberErrorCode.WITHDRAW_SETTLEMENT_NOT_READY));
        verify(checker, never()).requireSettled(anyLong(), anyLong());
        verify(other, never()).requireSettled(anyLong(), anyLong());
        verifyNoInteractions(blocks, withdrawal);
    }

    @Test
    @DisplayName("여러 구현체 중 정산 검사가 실패하면 회원 정보와 체험 차단을 변경하지 않는다")
    void stopsOnCheckerFailure() {
        // given
        allowMember();
        WithdrawalSettlementChecker other = mock(WithdrawalSettlementChecker.class);
        given(checker.supportedScopes()).willReturn(java.util.EnumSet.allOf(WithdrawalSettlementChecker.Scope.class));
        given(other.supportedScopes()).willReturn(java.util.Set.of(WithdrawalSettlementChecker.Scope.POINTS));
        doThrow(new IllegalStateException("정산 미완료")).when(other).requireSettled(1L, 0L);
        // when, then
        assertThatThrownBy(() -> processor(checker, other).withdraw(1L, new MemberWithdrawRequest("password", 0L)))
                .isInstanceOf(IllegalStateException.class);
        verify(other).requireSettled(1L, 0L);
        verifyNoInteractions(blocks, withdrawal);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(longs = {0L, 2500L, Long.MAX_VALUE})
    @DisplayName("사용자가 확인한 잔액은 0을 포함해 모든 정산 Checker에 그대로 전달한다")
    void forwardsAcknowledgedBalanceToEveryChecker(Long acknowledgedBalance) {
        // given
        allowMember();
        WithdrawalSettlementChecker other = mock(WithdrawalSettlementChecker.class);
        given(checker.supportedScopes()).willReturn(java.util.Set.of(
                WithdrawalSettlementChecker.Scope.PRODUCT_PAYMENT, WithdrawalSettlementChecker.Scope.POINTS));
        given(other.supportedScopes()).willReturn(java.util.Set.of(
                WithdrawalSettlementChecker.Scope.MEMBERSHIP_PAYMENT, WithdrawalSettlementChecker.Scope.AUTO_BILLING));
        // when
        processor(checker, other).withdraw(1L, new MemberWithdrawRequest("password", acknowledgedBalance));
        // then
        verify(checker).requireSettled(1L, acknowledgedBalance);
        verify(other).requireSettled(1L, acknowledgedBalance);
        verify(withdrawal).withdraw(any(Member.class), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("POINTS에서 잔액 불일치 409를 전달하면 예외를 바꾸거나 탈퇴 처리를 계속하지 않는다")
    void preservesPointConflictAndStopsWithdrawal() {
        // given
        allowMember();
        given(checker.supportedScopes()).willReturn(java.util.EnumSet.allOf(WithdrawalSettlementChecker.Scope.class));
        var error = mock(com.recipick.global.exception.ErrorCode.class);
        given(error.getStatus()).willReturn(org.springframework.http.HttpStatus.CONFLICT);
        given(error.getMessage()).willReturn("확인한 포인트 잔액이 변경되었습니다.");
        var conflict = new BusinessException(error);
        doThrow(conflict).when(checker).requireSettled(1L, 2500L);
        // when, then
        assertThatThrownBy(() -> processor().withdraw(1L, new MemberWithdrawRequest("password", 2500L)))
                .isSameAs(conflict);
        verifyNoInteractions(blocks, withdrawal);
    }

}
