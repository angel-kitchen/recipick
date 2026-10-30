package com.recipick.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.recipick.domain.member.entity.Member;
import com.recipick.domain.member.entity.Membership;
import com.recipick.domain.member.entity.enums.MembershipSource;
import com.recipick.domain.member.entity.enums.MembershipType;
import com.recipick.domain.member.exception.MemberErrorCode;
import com.recipick.domain.member.repository.MemberRepository;
import com.recipick.domain.member.repository.MembershipRepository;
import com.recipick.global.exception.BusinessException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MembershipCancelReservationServiceTest {
    private final MemberRepository members = mock(MemberRepository.class);
    private final MembershipRepository memberships = mock(MembershipRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-30T03:00:00Z"), ZoneId.of("Asia/Seoul"));
    private final MembershipCancelReservationService service = new MembershipCancelReservationService(members, memberships, clock);
    private final LocalDateTime now = LocalDateTime.now(clock);

    @Test
    @DisplayName("회원 잠금이 이용권 소유권 잠금 조회보다 먼저이며 해지 예약은 종료 시각을 바꾸지 않는다")
    void locksMemberBeforeMembership() {
        // given
        Membership membership = membership(now, now.plusDays(7));
        given(members.findByIdForUpdate(1L)).willReturn(Optional.of(Member.builder().build()));
        given(memberships.findByIdAndMemberIdForUpdate(10L, 1L)).willReturn(Optional.of(membership));
        // when
        var response = service.reserve(1L, 10L);
        // then
        var order = inOrder(members, memberships);
        order.verify(members).findByIdForUpdate(1L);
        order.verify(memberships).findByIdAndMemberIdForUpdate(10L, 1L);
        assertThat(response.status()).isEqualTo("CANCEL_RESERVED");
        assertThat(response.endAt()).isEqualTo(now.plusDays(7));
        assertThat(response.cancelReservedAt()).isEqualTo(now);
        assertThat(membership.isActive(now)).isTrue();
    }

    @Test
    @DisplayName("회원 잠금 조회에 대상이 없으면 이용권을 읽거나 변경하지 않는다")
    void rejectsMissingMemberBeforeMembershipLookup() {
        // given
        given(members.findByIdForUpdate(1L)).willReturn(Optional.empty());
        // when, then
        assertThatThrownBy(() -> service.reserve(1L, 10L)).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(MemberErrorCode.MEMBER_NOT_FOUND));
        verifyNoInteractions(memberships);
    }

    @Test
    @DisplayName("토큰 인증 후 탈퇴가 커밋됐어도 회원 잠금 단계에서 거절한다")
    void rejectsWithdrawnMemberBeforeMembershipLookup() {
        // given
        Member member = mock(Member.class);
        given(member.isWithdrawn()).willReturn(true);
        given(members.findByIdForUpdate(1L)).willReturn(Optional.of(member));
        // when, then
        assertThatThrownBy(() -> service.reserve(1L, 10L)).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(MemberErrorCode.MEMBER_NOT_FOUND));
        verifyNoInteractions(memberships);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    @DisplayName("기간 종료 시각부터는 활성 이용권이 아니므로 해지 예약을 거절한다")
    void rejectsAtAndAfterEnd(int secondsAfterEnd) {
        // given
        Membership membership = membership(now.minusDays(7), now.minusSeconds(secondsAfterEnd));
        given(members.findByIdForUpdate(1L)).willReturn(Optional.of(Member.builder().build()));
        given(memberships.findByIdAndMemberIdForUpdate(10L, 1L)).willReturn(Optional.of(membership));
        // when, then
        assertThatThrownBy(() -> service.reserve(1L, 10L)).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(MemberErrorCode.ACTIVE_MEMBERSHIP_NOT_FOUND));
        assertThat(membership.isAutoRenew()).isTrue();
        assertThat(membership.getCancelReservedAt()).isNull();
    }

    private Membership membership(LocalDateTime start, LocalDateTime end) {
        return Membership.builder().type(MembershipType.TRIAL).source(MembershipSource.TRIAL)
                .startAt(start).endAt(end).autoRenew(true).build();
    }
}
