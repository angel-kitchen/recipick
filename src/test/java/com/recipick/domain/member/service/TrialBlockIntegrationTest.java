package com.recipick.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.recipick.IntegrationTestSupport;
import com.recipick.domain.member.entity.Member;
import com.recipick.domain.member.entity.Membership;
import com.recipick.domain.member.entity.enums.MembershipSource;
import com.recipick.domain.member.entity.enums.MembershipType;
import com.recipick.domain.member.repository.MemberRepository;
import com.recipick.domain.member.repository.MembershipRepository;
import com.recipick.domain.member.repository.TrialBlockRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class TrialBlockIntegrationTest extends IntegrationTestSupport {
    @Autowired TrialBlockService service;
    @Autowired TrialPhoneHasher hasher;
    @Autowired TrialBlockRepository blocks;
    @Autowired MemberRepository members;
    @Autowired MembershipRepository memberships;
    @Autowired MembershipService membershipService;
    @Autowired Clock clock;

    private Member member() {
        String suffix = UUID.randomUUID().toString();
        return members.saveAndFlush(Member.builder().email(suffix + "@t.dev")
                .phone(suffix.substring(0, 20)).name("테스트").passwordHash("hash").build());
    }

    private void history(Member member, LocalDateTime now) {
        memberships.saveAndFlush(Membership.builder().member(member).type(MembershipType.PAID)
                .source(MembershipSource.ADMIN_GRANT).startAt(now.minusDays(31)).endAt(now.minusDays(1)).build());
    }

    @Test
    @DisplayName("휴대전화 형식을 정규화하고 원문 대신 64자리 키 해시를 사용한다")
    void normalizesPhone() {
        // given
        String phone = "010-1234-5678";
        // when
        String hash = hasher.hash(phone);
        // then
        assertThat(hash).matches("[0-9a-f]{64}").isEqualTo(hasher.hash("01012345678"));
    }

    @Test
    @DisplayName("관리자 지급 이력도 탈퇴 시점부터 1년간 차단한다")
    void recordsAdminHistory() {
        // given
        Member member = member();
        LocalDateTime now = LocalDateTime.now(clock).withNano(0);
        history(member, now);
        members.findByIdForUpdate(member.getId());
        // when
        service.recordWithdrawal(member.getId(), now);
        // then
        assertThat(blocks.existsByPhoneHashAndExpiresAtAfter(hasher.hash(member.getPhone()), now.plusYears(1).minusSeconds(1))).isTrue();
        assertThat(blocks.existsByPhoneHashAndExpiresAtAfter(hasher.hash(member.getPhone()), now.plusYears(1))).isFalse();
    }

    @Test
    @DisplayName("이용 이력 없는 재탈퇴는 기존 만료 시각을 연장하지 않는다")
    void preservesExistingExpiryWithoutHistory() {
        // given
        Member member = member();
        LocalDateTime now = LocalDateTime.now(clock).withNano(0);
        String hash = hasher.hash(member.getPhone());
        blocks.extendBlock(hash, now.plusDays(1), now.minusDays(364));
        members.findByIdForUpdate(member.getId());
        // when
        service.recordWithdrawal(member.getId(), now);
        // then
        assertThat(blocks.existsByPhoneHashAndExpiresAtAfter(hash, now.plusDays(1))).isFalse();
        assertThat(service.isBlocked(member.getPhone())).isTrue();
    }

    @Test
    @DisplayName("이용 이력이 있는 재탈퇴는 연장하고 오래된 재요청은 기간을 줄이지 않는다")
    void extendsWithoutShortening() {
        // given
        Member member = member();
        LocalDateTime now = LocalDateTime.now(clock).withNano(0);
        history(member, now);
        members.findByIdForUpdate(member.getId());
        // when
        service.recordWithdrawal(member.getId(), now);
        service.recordWithdrawal(member.getId(), now.minusDays(10));
        // then
        assertThat(blocks.existsByPhoneHashAndExpiresAtAfter(hasher.hash(member.getPhone()), now.plusYears(1).minusSeconds(1))).isTrue();
    }

    @Test
    @DisplayName("미삭제 만료 기록은 체험을 막지 않으며 정리 배치는 유효한 차단을 유지한다")
    void ignoresExpiredAndDeletesConditionally() {
        // given
        Member expired = member();
        Member blocked = member();
        LocalDateTime now = LocalDateTime.now(clock).withNano(0);
        blocks.extendBlock(hasher.hash(expired.getPhone()), now.minusSeconds(1), now.minusYears(1));
        blocks.extendBlock(hasher.hash(blocked.getPhone()), now.plusDays(1), now);
        // when
        boolean eligible = membershipService.isTrialEligible(expired.getId());
        boolean ineligible = membershipService.isTrialEligible(blocked.getId());
        service.deleteExpiredBatch(now);
        service.deleteExpiredBatch(now);
        // then
        assertThat(eligible).isTrue();
        assertThat(ineligible).isFalse();
        assertThat(service.isBlocked(blocked.getPhone())).isTrue();
        assertThat(blocks.findAll()).noneMatch(block -> block.getPhoneHash().equals(hasher.hash(expired.getPhone())));
    }
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;

    @Test
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    @DisplayName("같은 번호의 동시 저장은 한 행을 유지하고 가장 늦은 만료 시각을 보존한다")
    void concurrentUpserts() throws Exception {
        // given
        String hash = hasher.hash(UUID.randomUUID().toString());
        LocalDateTime now = LocalDateTime.now(clock).withNano(0);
        var executor = java.util.concurrent.Executors.newFixedThreadPool(4);
        var transaction = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        var start = new java.util.concurrent.CountDownLatch(1);
        try {
            // when
            var futures = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 1; i <= 8; i++) {
                int days = i;
                futures.add(executor.submit(() -> {
                    start.await();
                    transaction.executeWithoutResult(status -> blocks.extendBlock(hash, now.plusDays(days), now));
                    return null;
                }));
            }
            start.countDown();
            for (var future : futures) {
                future.get(15, java.util.concurrent.TimeUnit.SECONDS);
            }
            // then
            assertThat(blocks.findAll().stream().filter(block -> block.getPhoneHash().equals(hash)).toList())
                    .singleElement().satisfies(block -> assertThat(block.getExpiresAt()).isEqualTo(now.plusDays(8)));
        } finally {
            executor.shutdownNow();
            transaction.executeWithoutResult(status -> blocks.findAll().stream()
                    .filter(block -> block.getPhoneHash().equals(hash)).forEach(blocks::delete));
        }
    }

}
