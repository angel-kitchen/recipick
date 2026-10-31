package com.recipick.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.recipick.IntegrationTestSupport;
import com.recipick.domain.member.entity.Member;
import com.recipick.domain.member.entity.Membership;
import com.recipick.domain.member.entity.enums.MembershipSource;
import com.recipick.domain.member.entity.enums.MembershipType;
import com.recipick.domain.member.repository.MemberRepository;
import com.recipick.domain.member.repository.MembershipRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class MembershipLifecycleIntegrationTest extends IntegrationTestSupport {
    @Autowired MemberRepository members;
    @Autowired MembershipRepository memberships;
    @Autowired MembershipService service;
    @Autowired TrialBlockService blocks;
    @Autowired Clock clock;

    Member member;
    LocalDateTime now;

    @BeforeEach
    void setup() {
        String suffix = UUID.randomUUID().toString().substring(0, 20);
        member = members.saveAndFlush(Member.builder().email(suffix + "@lifecycle.test")
                .phone(suffix.substring(0, 20)).passwordHash("hash").build());
        now = LocalDateTime.now(clock).withNano(0);
    }

    private Membership paid(LocalDateTime activatedAt) {
        return Membership.builder().member(member).type(MembershipType.PAID).source(MembershipSource.SUBSCRIBE)
                .startAt(now.minusDays(30)).activatedAt(activatedAt).endAt(now.minusHours(1))
                .autoRenew(true).build();
    }

    @Test
    @DisplayName("결제 대기는 혜택과 이용 이력에서 제외하고 내 멤버십의 pending에 노출한다")
    void exposesPendingWithoutBenefitsOrUsageHistory() {
        // given
        Membership pending = memberships.saveAndFlush(paid(null));
        // when
        var response = service.getMe(member.getId());
        // then
        assertThat(response.active()).isFalse();
        assertThat(response.current()).isNull();
        assertThat(response.pending().membershipId()).isEqualTo(pending.getId());
        assertThat(response.pending().status()).isEqualTo("PAYMENT_PENDING");
        assertThat(response.pending().autoRenew()).isTrue();
        assertThat(response.trialEligible()).isTrue();
        assertThat(service.getTypeAt(member.getId(), now)).isEmpty();
        assertThat(service.existsBlocking(member.getId(), now)).isTrue();
        assertThat(members.searchMembers(null, null, true, null, null, now, PageRequest.of(0, 20)))
                .extracting(row -> row.memberId()).doesNotContain(member.getId());
    }

    @Test
    @DisplayName("전환 대기는 혜택 없이 조회하며 예약하면 만료되어 pending과 차단에서 제외한다")
    void exposesAndClosesConversionPending() {
        // given
        LocalDateTime start = now.minusDays(8);
        Membership trial = memberships.saveAndFlush(Membership.builder().member(member).type(MembershipType.TRIAL)
                .source(MembershipSource.TRIAL).startAt(start).activatedAt(start).endAt(now.minusDays(1))
                .autoRenew(true).build());
        // when
        var before = service.getMe(member.getId());
        trial.reserveCancellation(now);
        memberships.flush();
        var after = service.getMe(member.getId());
        // then
        assertThat(before.active()).isFalse();
        assertThat(before.pending().status()).isEqualTo("CONVERSION_PENDING");
        assertThat(before.trialEligible()).isFalse();
        assertThat(after.pending()).isNull();
        assertThat(service.existsBlocking(member.getId(), now)).isFalse();
    }

    @Test
    @DisplayName("유예 혜택은 회원 조회·적립률 유형·관리자 검색에 일치하며 유예 끝은 포함하지 않는다")
    void includesGraceAcrossQueriesAndExcludesItsEnd() {
        // given
        LocalDateTime graceEnd = now.plusDays(2);
        Membership membership = paid(now.minusDays(30));
        ReflectionTestUtils.setField(membership, "graceEndAt", graceEnd);
        // 최종 갱신 실패로 자동갱신만 꺼진 경우에도 남은 유예 혜택은 유지한다.
        ReflectionTestUtils.setField(membership, "autoRenew", false);
        memberships.saveAndFlush(membership);
        // when, then
        assertThat(service.isActive(member.getId())).isTrue();
        assertThat(service.getMe(member.getId()).pending()).isNull();
        assertThat(service.getSummary(member.getId(), now).active()).isTrue();
        assertThat(service.getTypeAt(member.getId(), graceEnd.minusSeconds(1))).contains(MembershipType.PAID);
        assertThat(service.getTypeAt(member.getId(), graceEnd)).isEmpty();
        assertThat(service.existsBlocking(member.getId(), graceEnd)).isFalse();
        assertThat(memberships.findActiveMembershipsForMembers(List.of(member.getId()), now)).hasSize(1);
        assertThat(members.searchMembers(null, null, true, MembershipType.PAID, MembershipSource.SUBSCRIBE,
                now, PageRequest.of(0, 20))).extracting(row -> row.memberId()).contains(member.getId());
        assertThat(membership.isActive(graceEnd)).isFalse();
    }

    @Test
    @DisplayName("적립률 유형은 시작 시각이 지났어도 실제 활성화 시각 전에는 반환하지 않는다")
    void excludesTimeBeforeActualActivation() {
        // given
        Membership membership = Membership.builder().member(member).type(MembershipType.PAID)
                .source(MembershipSource.SUBSCRIBE).startAt(now.minusMinutes(5)).activatedAt(now)
                .endAt(now.plusMonths(1)).build();
        memberships.saveAndFlush(membership);
        // when, then
        assertThat(service.getTypeAt(member.getId(), now.minusMinutes(2))).isEmpty();
        assertThat(service.getTypeAt(member.getId(), now)).contains(MembershipType.PAID);
    }

    @Test
    @DisplayName("활성화 없이 실패한 유료 이력은 체험 자격을 잃거나 탈퇴 번호 차단을 만들지 않는다")
    void ignoresUnactivatedFailureForTrialAndWithdrawalBlock() {
        // given
        Membership failed = paid(null);
        ReflectionTestUtils.setField(failed, "terminatedAt", now);
        memberships.saveAndFlush(failed);
        // when
        blocks.recordWithdrawal(member.getId(), now);
        // then
        assertThat(service.isTrialEligible(member.getId())).isTrue();
        assertThat(service.existsBlocking(member.getId(), now)).isFalse();
        assertThat(blocks.isBlocked(member.getPhone())).isFalse();
    }
}
