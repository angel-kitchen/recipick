package com.recipick.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.recipick.domain.member.entity.Membership;
import com.recipick.domain.member.entity.enums.MembershipSource;
import com.recipick.domain.member.entity.enums.MembershipType;
import com.recipick.domain.member.exception.MemberErrorCode;
import com.recipick.domain.member.repository.MemberRepository;
import com.recipick.domain.member.repository.MembershipRepository;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.exception.CommonErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

class MembershipAccessServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-14T01:00:00Z"),
            ZoneId.of("Asia/Seoul"));
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);

    private final MembershipRepository memberships = mock(MembershipRepository.class);
    private final MemberRepository members = mock(MemberRepository.class);
    private final MembershipService service = new MembershipService(memberships, members, CLOCK);

    @Test
    @DisplayName("활성 검증은 주입된 한국 시간 Clock을 기준으로 조회하고 활성 회원을 허용한다")
    void usesInjectedClockAndAllowsActiveMember() {
        // given
        given(members.existsByIdAndWithdrawnAtIsNull(1L)).willReturn(true);
        Membership membership = Membership.builder().type(MembershipType.TRIAL).source(MembershipSource.TRIAL)
                .startAt(NOW).endAt(NOW.plusDays(7)).build();
        given(memberships.findActiveMemberships(1L, NOW, PageRequest.of(0, 1))).willReturn(List.of(membership));

        // when
        service.requireActiveMembership(1L);

        // then
        assertThat(service.isActive(1L)).isTrue();
        verify(memberships, org.mockito.Mockito.times(2))
                .findActiveMemberships(1L, NOW, PageRequest.of(0, 1));
    }

    @Test
    @DisplayName("활성 이용권이 없는 회원의 전용 기능 검증은 MEMBERSHIP-001로 거절한다")
    void rejectsInactiveMember() {
        // given
        given(members.existsByIdAndWithdrawnAtIsNull(1L)).willReturn(true);
        given(memberships.findActiveMemberships(1L, NOW, PageRequest.of(0, 1))).willReturn(List.of());

        // when
        boolean active = service.isActive(1L);

        // then
        assertThat(active).isFalse();
        assertThatThrownBy(() -> service.requireActiveMembership(1L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.MEMBERSHIP_REQUIRED));
    }

    @Test
    @DisplayName("없는 회원의 내부 조회는 MEMBER-001로 거절하고 이용권 조회를 하지 않는다")
    void rejectsMissingMember() {
        // given
        Long memberId = 99L;

        // when
        org.assertj.core.api.ThrowableAssert.ThrowingCallable query = () -> service.getTypeAt(memberId, NOW);

        // then
        assertThatThrownBy(query).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.MEMBER_NOT_FOUND));
        verifyNoInteractions(memberships);
    }

    @Test
    @DisplayName("특정 시점을 누락한 내부 조회는 입력 오류로 거절한다")
    void rejectsMissingTime() {
        // given
        LocalDateTime missingTime = null;

        // when
        org.assertj.core.api.ThrowableAssert.ThrowingCallable query = () -> service.getTypeAt(1L, missingTime);

        // then
        assertThatThrownBy(query).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT));
        verifyNoInteractions(members, memberships);
    }
}
