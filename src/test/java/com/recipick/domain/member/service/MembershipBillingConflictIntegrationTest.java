package com.recipick.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.recipick.IntegrationTestSupport;
import com.recipick.domain.member.dto.MembershipConsent;
import com.recipick.domain.member.entity.Member;
import com.recipick.domain.member.entity.Membership;
import com.recipick.domain.member.entity.enums.MembershipSource;
import com.recipick.domain.member.entity.enums.MembershipType;
import com.recipick.domain.member.exception.MemberErrorCode;
import com.recipick.domain.member.repository.MemberRepository;
import com.recipick.domain.member.repository.MembershipRepository;
import com.recipick.global.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class MembershipBillingConflictIntegrationTest extends IntegrationTestSupport {
    @Autowired MembershipBillingService billing;
    @Autowired MemberService memberService;
    @Autowired MemberRepository members;
    @Autowired MembershipRepository memberships;
    @Autowired JdbcTemplate jdbc;

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 2, 12, 0);
    private static final LocalDateTime CREATED = NOW.minusDays(10);

    private Member member() {
        String suffix = UUID.randomUUID().toString().substring(0, 20);
        return members.saveAndFlush(Member.builder().email(suffix + "@conflict.test")
                .phone(suffix).passwordHash("hash").build());
    }

    private Membership paid(Member owner, LocalDateTime start, LocalDateTime end) {
        return memberships.saveAndFlush(Membership.builder().member(owner).type(MembershipType.PAID)
                .source(MembershipSource.SUBSCRIBE).startAt(start).activatedAt(start).endAt(end).autoRenew(false).build());
    }

    private void createdAt(Membership value, LocalDateTime at) {
        // DB 시각을 고정해 JPA 객체의 나노초 정밀도와 무관하게 동률·생성 순서를 검증한다.
        jdbc.update("UPDATE membership SET created_at = ? WHERE id = ?", at, value.getId());
    }

    @Test
    @DisplayName("대상 이용권 자체는 활성 상태여도 충돌로 세지 않는다")
    void excludesTargetItself() {
        // given
        Member owner = member();
        Membership target = paid(owner, NOW.minusDays(1), NOW.plusMonths(1));
        memberService.lockActiveMember(owner.getId());
        // when, then
        assertThat(billing.existsConflictingMembershipForBilling(owner.getId(), target.getId(), NOW)).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"ACTIVE,true", "PAYMENT_PENDING,true", "CONVERSION_PENDING,true", "EXPIRED,false", "FUTURE,false"})
    @DisplayName("더 오래된 다른 이용권도 활성·결제 대기·전환 대기면 충돌이고 단순 만료·미시작이면 제외한다")
    void checksExistingBlockingStates(String state, boolean expected) {
        // given
        Member owner = member();
        Membership target = paid(owner, NOW.minusMonths(1), NOW.minusDays(5));
        Membership other = switch (state) {
            case "PAYMENT_PENDING" -> memberships.saveAndFlush(Membership.pendingPaid(owner,
                    MembershipSource.SUBSCRIBE, NOW, NOW.plusMonths(1), new MembershipConsent(3900L, "v1", NOW)));
            case "CONVERSION_PENDING" -> memberships.saveAndFlush(Membership.builder().member(owner)
                    .type(MembershipType.TRIAL).source(MembershipSource.TRIAL).startAt(NOW.minusDays(8))
                    .activatedAt(NOW.minusDays(8)).endAt(NOW.minusDays(1)).autoRenew(true).build());
            case "EXPIRED" -> paid(owner, NOW.minusMonths(2), NOW.minusMonths(1));
            case "FUTURE" -> paid(owner, NOW.plusDays(1), NOW.plusMonths(1));
            default -> paid(owner, NOW.minusDays(1), NOW.plusMonths(1));
        };
        createdAt(target, CREATED);
        createdAt(other, CREATED.minusDays(1));
        memberService.lockActiveMember(owner.getId());
        // when, then
        assertThat(billing.existsConflictingMembershipForBilling(owner.getId(), target.getId(), NOW)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"-1,false", "0,true", "1,true"})
    @DisplayName("미종료 만료 이력은 생성 시각을 우선하고 같은 시각이면 더 큰 ID를 후속 생성으로 판정한다")
    void comparesCreatedTimeThenId(int createdOffset, boolean expected) {
        // given
        Member owner = member();
        Membership target = paid(owner, NOW.minusMonths(2), NOW.minusMonths(1));
        Membership laterId = paid(owner, NOW.minusMonths(1), NOW.minusDays(5));
        createdAt(target, CREATED);
        createdAt(laterId, CREATED.plusSeconds(createdOffset));
        memberService.lockActiveMember(owner.getId());
        // when, then
        assertThat(laterId.getId()).isGreaterThan(target.getId());
        assertThat(billing.existsConflictingMembershipForBilling(owner.getId(), target.getId(), NOW)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"0,false", "1,true"})
    @DisplayName("ID가 작아도 생성 시각이 나중이면 후속이며 같은 시각의 작은 ID는 제외한다")
    void prioritizesCreatedTimeOverSmallerId(int offset, boolean expected) {
        // given
        Member owner = member();
        Membership smallerId = paid(owner, NOW.minusMonths(1), NOW.minusDays(5));
        Membership target = paid(owner, NOW.minusMonths(2), NOW.minusMonths(1));
        createdAt(target, CREATED);
        createdAt(smallerId, CREATED.plusSeconds(offset));
        memberService.lockActiveMember(owner.getId());
        // when, then
        assertThat(smallerId.getId()).isLessThan(target.getId());
        assertThat(billing.existsConflictingMembershipForBilling(owner.getId(), target.getId(), NOW)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"REFUNDED", "FIRST_PAYMENT_FAILED"})
    @DisplayName("더 나중에 생성돼도 환불·첫 결제 실패로 terminatedAt이 있으면 충돌에서 제외한다")
    void excludesTerminatedLaterRows(String state) {
        // given
        Member owner = member();
        Membership target = paid(owner, NOW.minusMonths(2), NOW.minusMonths(1));
        Membership other;
        if (state.equals("REFUNDED")) {
            other = paid(owner, NOW.minusDays(2), NOW.plusMonths(1));
            other.terminateAfterRefund(NOW.minusDays(1));
        } else {
            other = Membership.pendingPaid(owner, MembershipSource.SUBSCRIBE, NOW, NOW.plusMonths(1),
                    new MembershipConsent(3900L, "v1", NOW));
            other.closeUnpaid(NOW);
            memberships.saveAndFlush(other);
        }
        memberships.flush();
        createdAt(target, CREATED);
        createdAt(other, CREATED.plusDays(1));
        memberService.lockActiveMember(owner.getId());
        // when, then
        assertThat(billing.existsConflictingMembershipForBilling(owner.getId(), target.getId(), NOW)).isFalse();
    }

    @Test
    @DisplayName("다른 회원의 활성·후속 생성 이용권은 충돌에 포함하지 않는다")
    void isolatesOwner() {
        // given
        Member owner = member();
        Membership target = paid(owner, NOW.minusMonths(2), NOW.minusMonths(1));
        Membership other = paid(member(), NOW.minusDays(1), NOW.plusMonths(1));
        createdAt(target, CREATED);
        createdAt(other, CREATED.plusDays(1));
        memberService.lockActiveMember(owner.getId());
        // when, then
        assertThat(billing.existsConflictingMembershipForBilling(owner.getId(), target.getId(), NOW)).isFalse();
    }

    @Test
    @DisplayName("없는 대상 또는 타인 대상 이용권은 충돌 없음으로 통과시키지 않고 MEMBERSHIP-006으로 거절한다")
    void rejectsMissingOrForeignTarget() {
        // given
        Member owner = member();
        Membership foreign = paid(member(), NOW.minusDays(1), NOW.plusMonths(1));
        memberService.lockActiveMember(owner.getId());
        // when, then
        for (Long id : new Long[]{-1L, foreign.getId()}) {
            assertThatThrownBy(() -> billing.existsConflictingMembershipForBilling(owner.getId(), id, NOW))
                    .isInstanceOfSatisfying(BusinessException.class,
                            error -> assertThat(error.getErrorCode()).isEqualTo(MemberErrorCode.ACTIVE_MEMBERSHIP_NOT_FOUND));
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("충돌 조회는 바깥 트랜잭션이 없으면 실행하지 않는다")
    void requiresOuterTransaction() {
        // given, when, then
        assertThatThrownBy(() -> billing.existsConflictingMembershipForBilling(1L, 1L, NOW))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    @Transactional(readOnly = true)
    @DisplayName("충돌 조회는 읽기 전용 트랜잭션을 거절한다")
    void rejectsReadOnlyTransaction() {
        // given, when, then
        assertThatThrownBy(() -> billing.existsConflictingMembershipForBilling(1L, 1L, NOW))
                .isInstanceOf(IllegalStateException.class);
    }
}
