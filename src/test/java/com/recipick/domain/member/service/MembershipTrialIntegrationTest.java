package com.recipick.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.recipick.IntegrationTestSupport;
import com.recipick.domain.member.dto.MembershipConsent;
import com.recipick.domain.member.dto.request.MembershipTrialStartRequest;
import com.recipick.domain.member.entity.Member;
import com.recipick.domain.member.entity.Membership;
import com.recipick.domain.member.entity.enums.MembershipSource;
import com.recipick.domain.member.entity.enums.MembershipType;
import com.recipick.domain.member.exception.MemberErrorCode;
import com.recipick.domain.member.repository.MemberRepository;
import com.recipick.domain.member.repository.MembershipRepository;
import com.recipick.domain.member.repository.TrialBlockRepository;
import com.recipick.global.exception.BusinessException;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Transactional
class MembershipTrialIntegrationTest extends IntegrationTestSupport {
    @Autowired MembershipTrialService trials;
    @Autowired MemberService memberService;
    @Autowired MemberRepository members;
    @Autowired MembershipRepository memberships;
    @Autowired TrialBlockRepository blocks;
    @Autowired TrialPhoneHasher hasher;
    @Autowired EntityManager entityManager;
    @Autowired PlatformTransactionManager transactionManager;

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 2, 12, 0);

    private Member member() {
        String suffix = UUID.randomUUID().toString().substring(0, 20);
        return members.saveAndFlush(Member.builder().email(suffix + "@trial.test")
                .phone(suffix).name("체험 회원").passwordHash("hash").build());
    }

    private MembershipConsent consent() {
        return new MembershipTrialStartRequest(true, 3900L, "v1").toConsent(NOW);
    }

    private void assertError(Runnable action, MemberErrorCode expected) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(expected));
    }

    @Test
    @DisplayName("회원 잠금 이후 정확히 7일의 체험과 서버 시각의 자동결제 동의를 저장한다")
    void createsSevenDayActivatedTrial() {
        // given
        Member member = member();
        memberService.lockActiveMember(member.getId());
        // when
        var response = trials.start(member, consent(), NOW);
        memberships.flush();
        entityManager.clear();
        // then
        Membership saved = memberships.findById(response.membershipId()).orElseThrow();
        assertThat(response.type()).isEqualTo("TRIAL");
        assertThat(response.source()).isEqualTo("TRIAL");
        assertThat(response.startAt()).isEqualTo(NOW);
        assertThat(response.endAt()).isEqualTo(NOW.plusDays(7));
        assertThat(saved.getActivatedAt()).isEqualTo(NOW);
        assertThat(saved.isAutoRenew()).isTrue();
        assertThat(saved.getGraceEndAt()).isNull();
        assertThat(saved.getBillingAnchorDate()).isNull();
        assertThat(saved.getConsentedAmount()).isEqualTo(3900L);
        assertThat(saved.getConsentTextVersion()).isEqualTo("v1");
        assertThat(saved.getAutopayConsentedAt()).isEqualTo(NOW);
        assertThat(saved.isActive(NOW.plusDays(7).minusSeconds(1))).isTrue();
        assertThat(saved.isActive(NOW.plusDays(7))).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = MembershipSource.class, names = {"TRIAL", "ADMIN_GRANT", "SUBSCRIBE"})
    @DisplayName("만료·종료된 이력도 실제 활성화됐으면 무료체험을 다시 시작하지 않는다")
    void rejectsActivatedHistory(MembershipSource source) {
        // given
        Member member = member();
        LocalDateTime start = NOW.minusDays(40);
        Membership history = Membership.builder().member(member)
                .type(source == MembershipSource.TRIAL ? MembershipType.TRIAL : MembershipType.PAID)
                .source(source).startAt(start).activatedAt(start).endAt(start.plusDays(30)).autoRenew(false).build();
        if (source == MembershipSource.SUBSCRIBE) {
            history.terminateAfterRefund(start.plusDays(1));
        }
        memberships.saveAndFlush(history);
        memberService.lockActiveMember(member.getId());
        // when, then
        assertError(() -> trials.start(member, consent(), NOW), MemberErrorCode.TRIAL_NOT_ELIGIBLE);
        assertThat(memberships.findIdsByMemberId(member.getId())).hasSize(1);
    }

    @Test
    @DisplayName("미활성 결제 대기가 있으면 체험을 시작하지 않고 실패 종료된 대기 이력은 허용한다")
    void blocksPendingButAllowsFailedUnactivatedHistory() {
        // given
        Member member = member();
        Membership pending = Membership.pendingPaid(member, MembershipSource.SUBSCRIBE, NOW.minusDays(1),
                NOW.plusMonths(1), consent());
        memberships.saveAndFlush(pending);
        memberService.lockActiveMember(member.getId());
        // when, then
        assertError(() -> trials.start(member, consent(), NOW), MemberErrorCode.MEMBERSHIP_ALREADY_ACTIVE);
        pending.closeUnpaid(NOW);
        memberships.flush();
        assertThat(trials.start(member, consent(), NOW).type()).isEqualTo("TRIAL");
        assertThat(memberships.findIdsByMemberId(member.getId())).hasSize(2);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 1})
    @DisplayName("전화번호 차단은 만료 직전까지 적용하며 만료 시각부터 체험을 허용한다")
    void appliesPhoneBlockUntilExclusiveExpiry(int seconds) {
        // given
        Member member = member();
        blocks.extendBlock(hasher.hash(member.getPhone()), NOW.plusSeconds(seconds), NOW.minusYears(1));
        memberService.lockActiveMember(member.getId());
        // when, then
        if (seconds > 0) {
            assertError(() -> trials.start(member, consent(), NOW), MemberErrorCode.TRIAL_NOT_ELIGIBLE);
        } else {
            assertThat(trials.start(member, consent(), NOW).source()).isEqualTo("TRIAL");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"NULL", "AMOUNT", "VERSION", "LONG_VERSION", "TIME", "OTHER_TIME"})
    @DisplayName("동의 누락·다른 금액·잘못된 버전·다른 동의 시각은 저장 전에 거절한다")
    void rejectsInvalidConsent(String kind) {
        // given
        Member member = member();
        memberService.lockActiveMember(member.getId());
        MembershipConsent invalid = switch (kind) {
            case "NULL" -> null;
            case "AMOUNT" -> new MembershipConsent(3901L, "v1", NOW);
            case "VERSION" -> new MembershipConsent(3900L, " ", NOW);
            case "LONG_VERSION" -> new MembershipConsent(3900L, "x".repeat(21), NOW);
            case "TIME" -> new MembershipConsent(3900L, "v1", null);
            default -> new MembershipConsent(3900L, "v1", NOW.minusSeconds(1));
        };
        // when, then
        assertError(() -> trials.start(member, invalid, NOW), MemberErrorCode.AUTOPAY_CONSENT_REQUIRED);
        assertThat(memberships.findIdsByMemberId(member.getId())).isEmpty();
    }

    @Test
    @DisplayName("자동결제에 동의하지 않은 요청은 동의 데이터로 변환하지 않는다")
    void rejectsUncheckedAgreement() {
        // given
        var request = new MembershipTrialStartRequest(false, 3900L, "v1");
        // when, then
        assertError(() -> request.toConsent(NOW), MemberErrorCode.AUTOPAY_CONSENT_REQUIRED);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("체험 생성은 바깥 트랜잭션 없이 실행하지 않는다")
    void requiresOuterTransaction() {
        // given, when, then
        assertThatThrownBy(() -> trials.start(null, consent(), NOW)).isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    @Transactional(readOnly = true)
    @DisplayName("읽기 전용 트랜잭션에서는 체험 생성을 거절한다")
    void rejectsReadOnlyTransaction() {
        // given, when, then
        assertThatThrownBy(() -> trials.start(null, consent(), NOW)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("회원 잠금을 공유하는 동시 체험 시작은 한 건만 생성한다")
    void serializesTrialCreationUnderMemberLock() throws Exception {
        // given
        Member member = member();
        var pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        java.util.concurrent.Callable<Boolean> attempt = () -> {
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("동시 시작 대기 시간 초과");
            }
            try {
                return new TransactionTemplate(transactionManager).execute(status -> {
                    Member locked = memberService.getMemberForUpdate(member.getId());
                    trials.start(locked, consent(), NOW);
                    return true;
                });
            } catch (BusinessException error) {
                assertThat(error.getErrorCode()).isEqualTo(MemberErrorCode.MEMBERSHIP_ALREADY_ACTIVE);
                return false;
            }
        };
        try {
            var first = pool.submit(attempt);
            var second = pool.submit(attempt);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            // when
            start.countDown();
            boolean a = first.get(20, TimeUnit.SECONDS);
            boolean b = second.get(20, TimeUnit.SECONDS);
            // then
            assertThat(a ^ b).isTrue();
            assertThat(memberships.findIdsByMemberId(member.getId())).hasSize(1);
        } finally {
            start.countDown();
            pool.shutdownNow();
            pool.awaitTermination(5, TimeUnit.SECONDS);
            memberships.deleteAllById(memberships.findIdsByMemberId(member.getId()));
            members.deleteById(member.getId());
        }
    }
}
