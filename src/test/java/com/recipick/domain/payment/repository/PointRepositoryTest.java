package com.recipick.domain.payment.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.recipick.IntegrationTestSupport;
import com.recipick.domain.payment.entity.MemberPoint;
import com.recipick.domain.payment.entity.PointEarning;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.hibernate.engine.spi.SessionImplementor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

// 포인트 잠금 조회의 조건과 순서를 실제 MySQL로 본다. 회원 ID는 다른 테스트와 겹치지 않는 큰 값을 쓴다
@Transactional
class PointRepositoryTest extends IntegrationTestSupport {

    private static final Long MEMBER_ID = 9_100_001L;
    private static final long ORDER_ID_BASE = 9_100_000L;
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 28);
    private static final LocalDateTime NOW = TODAY.atTime(14, 0);

    @Autowired
    MemberPointRepository memberPointRepository;

    @Autowired
    PointEarningRepository pointEarningRepository;

    @Autowired
    EntityManager em;

    @Test
    @DisplayName("회원 포인트 행이 없으면 잔액 0으로 만들고, 이미 있으면 다시 호출해도 기존 잔액을 그대로 둔다")
    void insertIfAbsentKeepsExistingBalance() {
        // given
        memberPointRepository.insertIfAbsent(MEMBER_ID, NOW);
        MemberPoint created = memberPointRepository.findByMemberIdForUpdate(MEMBER_ID).orElseThrow();
        assertThat(created.getBalance()).isZero();
        created.increase(500L);
        em.flush();
        em.clear();

        // when
        memberPointRepository.insertIfAbsent(MEMBER_ID, NOW.plusMinutes(1));
        em.clear();

        // then
        MemberPoint found = memberPointRepository.findByMemberIdForUpdate(MEMBER_ID).orElseThrow();
        assertThat(found.getBalance()).isEqualTo(500L);
        assertThat(memberPointRepository.findAll()).filteredOn(point -> MEMBER_ID.equals(point.getMemberId()))
            .hasSize(1);
    }

    @Test
    @DisplayName("사용 가능 조회는 잔량이 있는 확정 적립 중 만료일이 오늘 이후인 것만 돌려준다 — 오늘 만료분은 포함")
    void findUsableExcludesPendingExpiredAndExhausted() {
        // given
        PointEarning expiresToday = completed(1L, 100L, TODAY.minusYears(1));
        PointEarning expiredYesterday = completed(2L, 100L, TODAY.minusYears(1).minusDays(1));
        PointEarning exhausted = completed(3L, 100L, TODAY);
        exhausted.use(100L);
        PointEarning pending = PointEarning.pending(MEMBER_ID, ORDER_ID_BASE + 4L, 100L);
        saveAll(expiresToday, expiredYesterday, exhausted, pending);

        // when
        List<PointEarning> usable = pointEarningRepository.findUsableForUpdate(MEMBER_ID, TODAY);

        // then
        assertThat(usable).extracting(PointEarning::getId).containsExactly(expiresToday.getId());
    }

    @Test
    @DisplayName("만료 조회는 만료일이 어제 이전이면서 잔량이 남은 확정 적립만 돌려준다 — 오늘 만료분과 잔량 0은 제외")
    void findExpiredOnlyPastExpiryWithRemaining() {
        // given
        PointEarning expiredYesterday = completed(1L, 100L, TODAY.minusYears(1).minusDays(1));
        PointEarning expiresToday = completed(2L, 100L, TODAY.minusYears(1));
        PointEarning exhaustedExpired = completed(3L, 100L, TODAY.minusYears(1).minusDays(1));
        exhaustedExpired.use(100L);
        saveAll(expiredYesterday, expiresToday, exhaustedExpired);

        // when
        List<PointEarning> expired = pointEarningRepository.findExpiredForUpdate(MEMBER_ID, TODAY);

        // then
        assertThat(expired).extracting(PointEarning::getId).containsExactly(expiredYesterday.getId());
    }

    @Test
    @DisplayName("사용 가능 잠금 조회는 만료일과 관계없이 id 오름차순으로 돌려준다 — 잠금 조회 순서(id)와 사용 순서(만료일 우선)는 다르고 사용 순서는 서비스가 정한다")
    void usableLockQueryReturnsIdAscending() {
        // given — 먼저 저장한 A(id 작음)는 12/31 만료, 나중 B(id 큼)는 11/30 만료
        PointEarning lotA = completed(1L, 100L, LocalDate.of(2025, 12, 31));
        PointEarning lotB = completed(2L, 100L, LocalDate.of(2025, 11, 30));
        saveAll(lotA, lotB);

        // when
        List<PointEarning> usable = pointEarningRepository.findUsableForUpdate(MEMBER_ID, TODAY);

        // then — 반환 순서는 A → B다. 만료가 빠른 B부터 쓰는 것은 2단계 서비스 테스트가 본다
        assertThat(usable).extracting(PointEarning::getId).containsExactly(lotA.getId(), lotB.getId());
        assertThat(lotA.getId()).isLessThan(lotB.getId());
        assertThat(lotB.getExpiresOn()).isBefore(lotA.getExpiresOn());
    }

    @Test
    @DisplayName("복원 대상 적립 건 잠금 조회는 넘긴 ID 순서와 관계없이 id 오름차순으로 돌려준다")
    void restoreLockQueryReturnsIdAscending() {
        // given
        PointEarning first = completed(1L, 100L, TODAY);
        PointEarning second = completed(2L, 100L, TODAY);
        PointEarning third = completed(3L, 100L, TODAY);
        saveAll(first, second, third);

        // when
        List<PointEarning> locked = pointEarningRepository.findAllByIdInForUpdate(
            List.of(third.getId(), first.getId(), second.getId()));

        // then
        assertThat(locked).extracting(PointEarning::getId)
            .containsExactly(first.getId(), second.getId(), third.getId());
    }

    @Test
    @DisplayName("주문 ID로 적립 건 엔티티를 읽지 않고 회원 ID만 꺼내며, 적립 건이 없는 주문은 비어 있다")
    void findMemberIdByOrderId() {
        // given
        PointEarning earning = PointEarning.pending(MEMBER_ID, ORDER_ID_BASE + 5L, 100L);
        saveAll(earning);

        // when
        Optional<Long> found = pointEarningRepository.findMemberIdByOrderId(ORDER_ID_BASE + 5L);
        Optional<Long> missing = pointEarningRepository.findMemberIdByOrderId(ORDER_ID_BASE + 6L);

        // then
        assertThat(found).contains(MEMBER_ID);
        assertThat(missing).isEmpty();
        assertThat(managedEntityCount()).isZero();
    }

    // 적립 확정일로부터 1년 뒤가 만료일이다
    private static PointEarning completed(Long orderId, long amount, LocalDate confirmedOn) {
        PointEarning earning = PointEarning.pending(MEMBER_ID, ORDER_ID_BASE + orderId, amount);
        earning.complete(confirmedOn.atTime(10, 0));
        return earning;
    }

    // 값만 조회했다면 영속성 컨텍스트에 올라간 엔티티가 없어야 한다
    private int managedEntityCount() {
        return em.unwrap(SessionImplementor.class).getPersistenceContextInternal().getNumberOfManagedEntities();
    }

    private void saveAll(PointEarning... earnings) {
        for (PointEarning earning : earnings) {
            pointEarningRepository.save(earning);
        }
        em.flush();
        em.clear();
    }
}
