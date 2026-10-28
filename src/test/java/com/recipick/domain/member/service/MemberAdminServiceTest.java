package com.recipick.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.recipick.domain.member.dto.MemberAdminRow;
import com.recipick.domain.member.dto.MemberMembershipData;
import com.recipick.domain.member.dto.request.MemberAdminSearchRequest;
import com.recipick.domain.member.entity.enums.MembershipType;
import com.recipick.domain.member.repository.MemberRepository;
import com.recipick.domain.member.repository.MembershipRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

@ExtendWith(MockitoExtension.class)
class MemberAdminServiceTest {

    @Mock
    private MemberRepository members;
    @Mock
    private MembershipRepository memberships;

    private MemberAdminService service;
    private LocalDateTime now;
    private MemberAdminSearchRequest request;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-23T01:00:00Z"), ZoneId.of("Asia/Seoul"));
        service = new MemberAdminService(members, memberships, clock, org.mockito.Mockito.mock(com.recipick.global.audit.AdminAuditRecorder.class));
        now = LocalDateTime.now(clock);
        request = new MemberAdminSearchRequest("test", null, null, null, null, null, null, null);
    }

    @Test
    @DisplayName("현재 페이지 회원을 묶어서 같은 기준 시각으로 멤버십과 체험 이력을 조회한다")
    void loadsMembershipDataInBatchesAtSameInstant() {
        // given
        List<MemberAdminRow> rows = List.of(new MemberAdminRow(1L, "a@test.com", "회원1", null, now),
                new MemberAdminRow(2L, "b@test.com", "회원2", 1L, now));
        when(members.searchMembers("test", null, null, null, null, now, request.toPageable()))
                .thenReturn(new PageImpl<>(rows, request.toPageable(), 2));
        when(memberships.findActiveMembershipsForMembers(List.of(1L, 2L), now))
                .thenReturn(List.of(new MemberMembershipData(2L, MembershipType.PAID, now.plusDays(30))));
        when(memberships.findMemberIdsWithTypeHistory(List.of(1L, 2L), MembershipType.TRIAL))
                .thenReturn(List.of(1L));

        // when
        var result = service.findMembers(request);

        // then
        assertThat(result.content().get(0).membershipActive()).isFalse();
        assertThat(result.content().get(0).membershipType()).isNull();
        assertThat(result.content().get(0).trialUsed()).isTrue();
        assertThat(result.content().get(1).membershipType()).isEqualTo("PAID");
        assertThat(result.content().get(1).membershipEndAt()).isEqualTo(now.plusDays(30));
        assertThat(result.content().get(1).trialUsed()).isFalse();
        verify(memberships).findActiveMembershipsForMembers(List.of(1L, 2L), now);
        verify(memberships).findMemberIdsWithTypeHistory(List.of(1L, 2L), MembershipType.TRIAL);
    }

    @Test
    @DisplayName("회원 조회 결과가 비어 있으면 멤버십 조회를 생략한다")
    void skipsMembershipQueriesForEmptyPage() {
        // given
        when(members.searchMembers("test", null, null, null, null, now, request.toPageable()))
                .thenReturn(new PageImpl<>(List.of(), request.toPageable(), 0));

        // when
        var result = service.findMembers(request);

        // then
        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isZero();
        assertThat(result.size()).isEqualTo(20);
        verifyNoInteractions(memberships);
    }
}
