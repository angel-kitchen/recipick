package com.recipick.domain.member.facade;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.recipick.domain.member.dto.request.MembershipTrialStartRequest;
import com.recipick.domain.member.entity.Member;
import com.recipick.domain.member.service.MemberService;
import com.recipick.domain.member.service.MembershipBillingService;
import com.recipick.domain.member.service.MembershipTrialService;
import com.recipick.domain.payment.service.BillingKeyService;
import com.recipick.domain.payment.service.MembershipPaymentQueryService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MembershipTrialFacadeTest {
    MemberService members = mock(MemberService.class);
    MembershipBillingService billing = mock(MembershipBillingService.class);
    MembershipTrialService trials = mock(MembershipTrialService.class);
    BillingKeyService keys = mock(BillingKeyService.class);
    MembershipPaymentQueryService payments = mock(MembershipPaymentQueryService.class);
    Clock clock = Clock.fixed(Instant.parse("2026-11-02T03:00:00Z"), ZoneId.of("Asia/Seoul"));
    MembershipTrialFacade facade = new MembershipTrialFacade(members, billing, trials, keys, payments, clock);
    MembershipTrialStartRequest request = new MembershipTrialStartRequest(true, 3900L, "v1");

    @Test
    @DisplayName("회원 잠금을 첫 호출로 하고 전체 이용권 정산 확인 후 같은 시각으로 생성한다")
    void locksBeforeAnyQueriesAndCreation() {
        // given
        Member member = Member.builder().build();
        given(members.getMemberForUpdate(1L)).willReturn(member);
        given(keys.hasActiveBillingKey(1L)).willReturn(true);
        given(billing.findMembershipIds(1L)).willReturn(List.of(3L, 5L));
        var now = LocalDateTime.now(clock);
        var consent = request.toConsent(now);
        // when
        facade.start(1L, request);
        // then
        var order = inOrder(members, trials, keys, billing, payments);
        order.verify(members).getMemberForUpdate(1L);
        order.verify(trials).validateStart(member, consent, now);
        order.verify(keys).hasActiveBillingKey(1L);
        order.verify(billing).findMembershipIds(1L);
        order.verify(payments).existsUnresolved(List.of(3L, 5L));
        order.verify(trials).start(member, consent, now);
    }

    @Test
    @DisplayName("미정산 확인 오류를 그대로 전파하고 체험을 생성하지 않는다")
    void propagatesPaymentQueryFailure() {
        // given
        given(keys.hasActiveBillingKey(1L)).willReturn(true);
        given(billing.findMembershipIds(1L)).willReturn(List.of());
        given(payments.existsUnresolved(List.of())).willThrow(new IllegalStateException("정산 조회 실패"));
        // when, then
        assertThatThrownBy(() -> facade.start(1L, request)).isInstanceOf(IllegalStateException.class);
        verify(trials, never()).start(any(), any(), any());
    }
}
