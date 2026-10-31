package com.recipick.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.recipick.IntegrationTestSupport;
import com.recipick.domain.member.dto.response.MemberMeResponse.MembershipSummary;
import com.recipick.domain.member.entity.Member;
import com.recipick.domain.member.entity.Membership;
import com.recipick.domain.member.entity.enums.MembershipSource;
import com.recipick.domain.member.entity.enums.MembershipType;
import com.recipick.domain.member.repository.MemberRepository;
import com.recipick.domain.member.repository.MembershipRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class MembershipSummaryIntegrationTest extends IntegrationTestSupport {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 14, 10, 0);

    @Autowired
    private MembershipService service;
    @Autowired
    private MembershipRepository memberships;
    @Autowired
    private MemberRepository members;

    @ParameterizedTest
    @CsvSource({"0,7,true", "-1,1,true", "-7,0,false", "1,7,false", "-10,-1,false"})
    @DisplayName("멤버십은 시작 시각 포함·종료 시각 미포함으로 활성 여부를 판정한다")
    void checksTimeBoundaries(int startDays, int endDays, boolean active) {
        // given
        Member member = member();
        memberships.saveAndFlush(membership(member, startDays, endDays, MembershipType.TRIAL));

        // when
        MembershipSummary summary = service.getSummary(member.getId(), NOW);

        // then
        assertThat(summary.active()).isEqualTo(active);
        assertThat(summary.type()).isEqualTo(active ? "TRIAL" : null);
        assertThat(summary.endAt()).isEqualTo(active ? NOW.plusDays(endDays) : null);
        assertThat(service.isTrialEligible(member.getId())).isFalse();
    }

    @Test
    @DisplayName("환불로 즉시 종료된 이용권은 기간이 남아 있어도 비활성이다")
    void excludesTerminatedMembership() {
        // given
        Member member = member();
        Membership membership = membership(member, -1, 29, MembershipType.PAID);
        membership.terminateAfterRefund(NOW.minusHours(1));
        memberships.saveAndFlush(membership);

        // when
        MembershipSummary summary = service.getSummary(member.getId(), NOW);

        // then
        assertThat(summary.active()).isFalse();
        assertThat(summary.type()).isNull();
        assertThat(summary.endAt()).isNull();
    }

    @Test
    @DisplayName("자동갱신 해지 예약은 남은 기간의 멤버십 혜택을 유지한다")
    void keepsCancellationReservedMembershipActive() {
        // given
        Member member = member();
        Membership membership = membership(member, -1, 29, MembershipType.PAID);
        membership.reserveCancellation(NOW);
        memberships.saveAndFlush(membership);

        // when
        MembershipSummary summary = service.getSummary(member.getId(), NOW);

        // then
        assertThat(summary.active()).isTrue();
        assertThat(summary.type()).isEqualTo("PAID");
    }

    @Test
    @DisplayName("다른 회원의 이용권은 본인의 활성 여부와 무료체험 자격에 영향을 주지 않는다")
    void isolatesMemberHistory() {
        // given
        Member owner = member();
        Member other = member();
        memberships.saveAndFlush(membership(other, -1, 29, MembershipType.PAID));

        // when
        MembershipSummary summary = service.getSummary(owner.getId(), NOW);

        // then
        assertThat(summary.active()).isFalse();
        assertThat(service.isTrialEligible(owner.getId())).isTrue();
    }

    private Member member() {
        return members.saveAndFlush(Member.builder().email(UUID.randomUUID() + "@ms.test")
                .phone(UUID.randomUUID().toString().substring(0, 20)).name("멤버십 검증")
                .passwordHash("test-password-hash").build());
    }

    private Membership membership(Member member, int startDays, int endDays, MembershipType type) {
        return Membership.builder().member(member).type(type)
                .source(type == MembershipType.TRIAL ? MembershipSource.TRIAL : MembershipSource.SUBSCRIBE)
                .startAt(NOW.plusDays(startDays)).activatedAt(NOW.plusDays(startDays)).endAt(NOW.plusDays(endDays)).autoRenew(true).build();
    }
}
