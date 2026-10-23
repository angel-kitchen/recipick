package com.recipick.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.recipick.IntegrationTestSupport;
import com.recipick.domain.member.entity.Member;
import com.recipick.domain.member.entity.Membership;
import com.recipick.domain.member.entity.enums.MembershipSource;
import com.recipick.domain.member.entity.enums.MembershipType;
import com.recipick.domain.member.repository.MemberRepository;
import com.recipick.domain.member.repository.MembershipRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class MembershipTypeAtIntegrationTest extends IntegrationTestSupport {

    private static final LocalDateTime START = LocalDateTime.of(2026, 10, 14, 10, 0);

    @Autowired
    private MembershipService service;
    @Autowired
    private MembershipRepository memberships;
    @Autowired
    private MemberRepository members;

    private Member member;

    @BeforeEach
    void setUp() {
        member = members.saveAndFlush(Member.builder().email(UUID.randomUUID() + "@mt.test")
                .phone(UUID.randomUUID().toString().substring(0, 20)).passwordHash("hash").build());
    }

    @ParameterizedTest
    @CsvSource({"-1,false", "0,true", "1,true", "6,true", "7,false", "8,false"})
    @DisplayName("특정 시점의 유형은 시작 포함·종료 미포함으로 조회한다")
    void checksPeriodBoundaries(int days, boolean active) {
        // given
        memberships.saveAndFlush(membership(MembershipType.TRIAL, START, START.plusDays(7)));

        // when
        Optional<MembershipType> type = service.getTypeAt(member.getId(), START.plusDays(days));

        // then
        assertThat(type).isEqualTo(active ? Optional.of(MembershipType.TRIAL) : Optional.empty());
    }

    @ParameterizedTest
    @CsvSource({"1,true", "2,false", "3,false"})
    @DisplayName("지금은 즉시 종료됐어도 종료 전 시점의 유형은 유지하며 종료 시점부터 제외한다")
    void preservesHistoricalTypeBeforeTermination(int days, boolean active) {
        // given
        Membership membership = membership(MembershipType.PAID, START, START.plusDays(30));
        membership.terminateAfterRefund(START.plusDays(2));
        memberships.saveAndFlush(membership);

        // when
        Optional<MembershipType> type = service.getTypeAt(member.getId(), START.plusDays(days));

        // then
        assertThat(type).isEqualTo(active ? Optional.of(MembershipType.PAID) : Optional.empty());
    }

    @Test
    @DisplayName("체험 종료와 유료 전환이 같은 시각이면 그 시각부터 유료 유형을 반환한다")
    void returnsPaidTypeAtTrialConversionBoundary() {
        // given
        memberships.saveAndFlush(membership(MembershipType.TRIAL, START, START.plusDays(7)));
        memberships.saveAndFlush(membership(MembershipType.PAID, START.plusDays(7), START.plusDays(37)));

        // when
        Optional<MembershipType> before = service.getTypeAt(member.getId(), START.plusDays(7).minusSeconds(1));
        Optional<MembershipType> boundary = service.getTypeAt(member.getId(), START.plusDays(7));

        // then
        assertThat(before).contains(MembershipType.TRIAL);
        assertThat(boundary).contains(MembershipType.PAID);
    }

    @Test
    @DisplayName("해지 예약 이후라도 종료 전 시점의 유형은 유지한다")
    void keepsTypeAfterCancellationReservation() {
        // given
        Membership membership = membership(MembershipType.PAID, START, START.plusDays(30));
        membership.reserveCancellation(START.plusDays(1));
        memberships.saveAndFlush(membership);

        // when
        Optional<MembershipType> type = service.getTypeAt(member.getId(), START.plusDays(2));

        // then
        assertThat(type).contains(MembershipType.PAID);
    }

    @Test
    @DisplayName("다른 회원의 이력은 해당 회원의 특정 시점 유형에 포함하지 않는다")
    void isolatesMemberHistory() {
        // given
        Member other = members.saveAndFlush(Member.builder().email(UUID.randomUUID() + "@other.test")
                .phone(UUID.randomUUID().toString().substring(0, 20)).passwordHash("hash").build());
        memberships.saveAndFlush(Membership.builder().member(other).type(MembershipType.TRIAL)
                .source(MembershipSource.TRIAL).startAt(START).endAt(START.plusDays(7)).build());

        // when
        Optional<MembershipType> type = service.getTypeAt(member.getId(), START.plusDays(1));

        // then
        assertThat(type).isEmpty();
    }

    private Membership membership(MembershipType type, LocalDateTime start, LocalDateTime end) {
        return Membership.builder().member(member).type(type)
                .source(type == MembershipType.TRIAL ? MembershipSource.TRIAL : MembershipSource.SUBSCRIBE)
                .startAt(start).endAt(end).autoRenew(true).build();
    }
}
