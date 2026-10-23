package com.recipick.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

import com.recipick.domain.member.dto.response.MembershipGrantResponse;
import com.recipick.domain.member.entity.Admin;
import com.recipick.domain.member.entity.Member;
import com.recipick.domain.member.entity.Membership;
import com.recipick.domain.member.entity.enums.AdminRole;
import com.recipick.domain.member.repository.AdminRepository;
import com.recipick.domain.member.repository.MembershipRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

class MembershipGrantServiceTest {

    @Test
    @DisplayName("지급은 회원 잠금부터 시작하고 한국 시간 기준 한 달이 아닌 정확히 30일을 생성한다")
    void locksMemberFirstAndGrantsExactlyThirtyDays() {
        // given
        Clock clock = Clock.fixed(Instant.parse("2026-01-30T15:00:00Z"), ZoneId.of("Asia/Seoul"));
        LocalDateTime start = LocalDateTime.now(clock);
        MemberService members = mock(MemberService.class);
        AdminRepository admins = mock(AdminRepository.class);
        MembershipRepository memberships = mock(MembershipRepository.class);
        Member member = Member.builder().build();
        Admin admin = Admin.builder().role(AdminRole.SUPER_ADMIN).active(true).build();
        ReflectionTestUtils.setField(admin, "id", 2L);
        given(members.getMemberForUpdate(1L)).willReturn(member);
        given(admins.findById(2L)).willReturn(Optional.of(admin));
        given(memberships.findActiveMemberships(1L, start, PageRequest.of(0, 1))).willReturn(List.of());
        given(memberships.save(any(Membership.class))).willAnswer(invocation -> {
            Membership value = invocation.getArgument(0);
            ReflectionTestUtils.setField(value, "id", 3L);
            return value;
        });
        MembershipGrantService service = new MembershipGrantService(members, admins, memberships, clock);

        // when
        MembershipGrantResponse result = service.grant(1L, 2L);

        // then
        assertThat(result.startAt()).isEqualTo(LocalDateTime.of(2026, 1, 31, 0, 0));
        assertThat(result.endAt()).isEqualTo(LocalDateTime.of(2026, 3, 2, 0, 0));
        assertThat(result.grantedByAdminId()).isEqualTo(2L);
        InOrder order = inOrder(members, admins, memberships);
        order.verify(members).getMemberForUpdate(1L);
        order.verify(admins).findById(2L);
        order.verify(memberships).findActiveMemberships(1L, start, PageRequest.of(0, 1));
        order.verify(memberships).save(any(Membership.class));
    }
}
