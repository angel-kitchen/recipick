package com.recipick.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class MembershipGrantServiceTest {

    @Test
    @DisplayName("지급은 전달된 한국 시간 기준으로 한 달이 아닌 정확히 30일을 생성한다")
    void grantsExactlyThirtyDaysFromProvidedTime() {
        // given
        Clock clock = Clock.fixed(Instant.parse("2026-01-30T15:00:00Z"), ZoneId.of("Asia/Seoul"));
        LocalDateTime start = LocalDateTime.now(clock);
        AdminRepository admins = mock(AdminRepository.class);
        MembershipRepository memberships = mock(MembershipRepository.class);
        Member member = Member.builder().build();
        ReflectionTestUtils.setField(member, "id", 1L);
        Admin admin = Admin.builder().role(AdminRole.SUPER_ADMIN).active(true).build();
        ReflectionTestUtils.setField(admin, "id", 2L);
        given(memberships.save(any(Membership.class))).willAnswer(invocation -> {
            Membership value = invocation.getArgument(0);
            ReflectionTestUtils.setField(value, "id", 3L);
            return value;
        });
        MembershipGrantService service = new MembershipGrantService(admins, memberships, mock(com.recipick.global.audit.AdminAuditRecorder.class));

        // when
        MembershipGrantResponse result = service.grant(member, admin, start);

        // then
        assertThat(result.startAt()).isEqualTo(LocalDateTime.of(2026, 1, 31, 0, 0));
        assertThat(result.endAt()).isEqualTo(LocalDateTime.of(2026, 3, 2, 0, 0));
        assertThat(result.grantedByAdminId()).isEqualTo(2L);
        org.mockito.Mockito.verify(memberships).save(any(Membership.class));
    }
}
