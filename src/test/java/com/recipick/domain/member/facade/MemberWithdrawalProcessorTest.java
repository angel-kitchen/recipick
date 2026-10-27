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
    MembershipService memberships = mock(MembershipService.class);
    TrialBlockService blocks = mock(TrialBlockService.class);
    MemberWithdrawalService withdrawal = mock(MemberWithdrawalService.class);
    WithdrawalSettlementChecker checker = mock(WithdrawalSettlementChecker.class);
    PasswordEncoder encoder = mock(PasswordEncoder.class);
    Clock clock = Clock.systemUTC();

    private MemberWithdrawalProcessor processor() {
        var factory = new StaticListableBeanFactory();
        factory.addBean("checker", checker);
        return new MemberWithdrawalProcessor(members, orders, memberships, blocks, withdrawal,
                factory.getBeanProvider(WithdrawalSettlementChecker.class), encoder, clock);
    }

    @Test
    @DisplayName("회원 잠금부터 시작하고 모든 조건 검사 이후 개인정보를 변경한다")
    void locksFirstAndMutatesLast() {
        // given
        Member member = Member.builder().passwordHash("encoded").build();
        given(members.getMemberForUpdate(1L)).willReturn(member);
        given(encoder.matches("password", "encoded")).willReturn(true);
        // when
        processor().withdraw(1L, new MemberWithdrawRequest("password"));
        // then
        var order = inOrder(members, encoder, orders, memberships, checker, blocks, withdrawal);
        order.verify(members).getMemberForUpdate(1L);
        order.verify(encoder).matches("password", "encoded");
        order.verify(orders).existsInProgressOrder(1L);
        order.verify(memberships).isActive(1L);
        order.verify(checker).requireSettled(1L);
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
        assertThatThrownBy(() -> processor().withdraw(1L, new MemberWithdrawRequest("password")))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> org.assertj.core.api.Assertions.assertThat(e.getErrorCode())
                                .isEqualTo(MemberErrorCode.WITHDRAW_BLOCKED_ORDER));
        verifyNoInteractions(memberships, checker, blocks, withdrawal);
    }
}
