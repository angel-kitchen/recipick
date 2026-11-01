package com.recipick.domain.member.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;

import com.recipick.domain.member.dto.response.MembershipGrantResponse;
import com.recipick.domain.member.entity.Admin;
import com.recipick.domain.member.entity.Member;
import com.recipick.domain.member.entity.enums.AdminRole;
import com.recipick.domain.member.exception.MemberErrorCode;
import com.recipick.domain.member.service.MemberService;
import com.recipick.domain.member.service.MembershipBillingService;
import com.recipick.domain.member.service.MembershipGrantService;
import com.recipick.global.exception.AuthErrorCode;
import com.recipick.global.exception.BusinessException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MembershipGrantFacadeTest {
    MemberService members = mock(MemberService.class);
    MembershipBillingService billing = mock(MembershipBillingService.class);
    MembershipGrantService grants = mock(MembershipGrantService.class);
    Clock clock = Clock.fixed(Instant.parse("2026-01-30T15:00:00Z"), ZoneId.of("Asia/Seoul"));
    MembershipGrantFacade facade = new MembershipGrantFacade(members, billing, grants, clock);

    @Test
    @DisplayName("회원 잠금부터 시작하고 관리자·차단 조건 확인 뒤 같은 시각으로 지급한다")
    void locksFirstAndValidatesBeforeGrant() {
        // given
        Member member = Member.builder().build();
        Admin admin = Admin.builder().role(AdminRole.SUPER_ADMIN).active(true).build();
        LocalDateTime now = LocalDateTime.now(clock);
        given(members.getMemberForUpdate(1L)).willReturn(member);
        given(grants.getGrantAdmin(2L)).willReturn(admin);
        MembershipGrantResponse expected = new MembershipGrantResponse(3L, 1L, "PAID", "ADMIN_GRANT",
                now, now.plusDays(30), 2L);
        given(grants.grant(member, admin, now)).willReturn(expected);
        // when
        var result = facade.grant(1L, 2L);
        // then
        var order = inOrder(members, grants, billing);
        order.verify(members).getMemberForUpdate(1L);
        order.verify(grants).getGrantAdmin(2L);
        order.verify(billing).existsBlockingMembership(1L, now);
        order.verify(grants).grant(member, admin, now);
        assertThat(result).isEqualTo(expected);
        assertThat(now).isEqualTo(LocalDateTime.of(2026, 1, 31, 0, 0));
    }

    @Test
    @DisplayName("지급 차단 이용권이 있으면 생성과 감사 기록을 호출하지 않는다")
    void refusesBlockingMembershipBeforeGrant() {
        // given
        given(members.getMemberForUpdate(1L)).willReturn(Member.builder().build());
        given(grants.getGrantAdmin(2L)).willReturn(Admin.builder().role(AdminRole.SUPER_ADMIN).active(true).build());
        given(billing.existsBlockingMembership(1L, LocalDateTime.now(clock))).willReturn(true);
        // when, then
        assertThatThrownBy(() -> facade.grant(1L, 2L)).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(MemberErrorCode.MEMBERSHIP_ALREADY_ACTIVE));
        verify(grants, never()).grant(any(), any(), any());
    }

    @Test
    @DisplayName("관리자 검증 실패는 이용권 상태 조회보다 먼저 전달한다")
    void validatesAdminBeforeMembershipQuery() {
        // given
        given(members.getMemberForUpdate(1L)).willReturn(Member.builder().build());
        given(grants.getGrantAdmin(2L)).willThrow(new BusinessException(AuthErrorCode.INACTIVE_ADMIN));
        // when, then
        assertThatThrownBy(() -> facade.grant(1L, 2L)).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(AuthErrorCode.INACTIVE_ADMIN));
        org.mockito.Mockito.verifyNoInteractions(billing);
        verify(grants, never()).grant(any(), any(), any());
    }
}
