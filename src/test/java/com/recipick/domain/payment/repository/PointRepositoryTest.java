package com.recipick.domain.payment.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.recipick.IntegrationTestSupport;
import com.recipick.domain.payment.entity.MemberPoint;
import com.recipick.domain.payment.entity.PointEarning;
import com.recipick.domain.payment.entity.PointHistory;
import com.recipick.domain.payment.entity.enums.PointHistoryType;
import com.recipick.domain.payment.repository.dto.PointExpirySummary;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.hibernate.engine.spi.SessionImplementor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
    PointHistoryRepository pointHistoryRepository;

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

    @Test
    @DisplayName("만료 대상 회원은 잔량이 남은 어제 이전 만료 확정 적립이 있는 회원만, 회원 ID 순으로 마지막으로 본 회원 다음부터 돌려준다")
    void findExpirationCandidateMemberIds() {
        // given — 회원 a·b·c는 만료 대상(a는 두 건), d는 오늘 만료, e는 잔량 0, f는 적립 예정
        long a = 9_100_101L;
        long b = 9_100_102L;
        long c = 9_100_103L;
        long d = 9_100_104L;
        long e = 9_100_105L;
        long f = 9_100_106L;
        PointEarning exhausted = completedFor(e, 15L, 100L, TODAY.minusYears(1).minusDays(1));
        exhausted.use(100L);
        saveAll(completedFor(a, 11L, 100L, TODAY.minusYears(1).minusDays(1)),
            completedFor(a, 12L, 100L, TODAY.minusYears(2)),
            completedFor(b, 13L, 100L, TODAY.minusYears(1).minusDays(1)),
            completedFor(c, 14L, 100L, TODAY.minusYears(1).minusDays(1)),
            completedFor(d, 16L, 100L, TODAY.minusYears(1)),
            exhausted,
            PointEarning.pending(f, ORDER_ID_BASE + 17L, 100L));

        // when
        List<Long> firstPage = pointEarningRepository.findExpirationCandidateMemberIds(
            TODAY, a - 1, PageRequest.of(0, 2));
        List<Long> nextPage = pointEarningRepository.findExpirationCandidateMemberIds(
            TODAY, b, PageRequest.of(0, 10));

        // then — 다른 테스트가 남긴 더 큰 회원 ID가 뒤에 있을 수 있어 앞부분과 제외 대상만 본다
        assertThat(firstPage).containsExactly(a, b);
        assertThat(nextPage).startsWith(c).doesNotContain(a, b, d, e, f);
    }

    @Test
    @DisplayName("사용 가능 잔량 합은 오늘 만료분까지 포함하고 어제 만료·잔량 0·적립 예정은 빼며, 적립이 없는 회원은 0이다")
    void sumUsableRemaining() {
        // given
        PointEarning exhausted = completed(3L, 100L, TODAY);
        exhausted.use(100L);
        saveAll(completed(1L, 100L, TODAY.minusYears(1)),
            completed(2L, 200L, TODAY.minusYears(1).minusDays(1)),
            exhausted,
            PointEarning.pending(MEMBER_ID, ORDER_ID_BASE + 4L, 300L));

        // when
        long usable = pointEarningRepository.sumUsableRemaining(MEMBER_ID, TODAY);
        long none = pointEarningRepository.sumUsableRemaining(MEMBER_ID + 1, TODAY);

        // then
        assertThat(usable).isEqualTo(100L);
        assertThat(none).isZero();
    }

    @Test
    @DisplayName("만료일별 사용 가능 잔량은 같은 날짜끼리 합치고 이른 날짜부터 돌려주며 어제 만료분은 빼고 오늘 만료분은 넣는다")
    void findUsableExpiries() {
        // given — 오늘 만료 100, 12/31 만료 500·700, 1/20 만료 300, 어제 만료 900
        saveAll(completed(1L, 500L, LocalDate.of(2025, 12, 31)),
            completed(2L, 700L, LocalDate.of(2025, 12, 31)),
            completed(3L, 300L, LocalDate.of(2026, 1, 20)),
            completed(4L, 900L, TODAY.minusYears(1).minusDays(1)),
            completed(5L, 100L, TODAY.minusYears(1)));

        // when
        List<PointExpirySummary> expiries =
            pointEarningRepository.findUsableExpiries(MEMBER_ID, TODAY, PageRequest.of(0, 10));
        List<PointExpirySummary> nearest =
            pointEarningRepository.findUsableExpiries(MEMBER_ID, TODAY, PageRequest.of(0, 1));
        List<PointExpirySummary> none =
            pointEarningRepository.findUsableExpiries(MEMBER_ID + 1, TODAY, PageRequest.of(0, 1));

        // then
        assertThat(expiries).extracting(PointExpirySummary::getExpiresOn, PointExpirySummary::getAmount)
            .containsExactly(
                tuple(TODAY, 100L),
                tuple(LocalDate.of(2026, 12, 31), 1_200L),
                tuple(LocalDate.of(2027, 1, 20), 300L));
        assertThat(nearest).singleElement().satisfies(first -> {
            assertThat(first.getExpiresOn()).isEqualTo(TODAY);
            assertThat(first.getAmount()).isEqualTo(100L);
        });
        assertThat(none).isEmpty();
    }

    @Test
    @DisplayName("회원 적립 건 페이지는 상태와 관계없이 그 회원 것만 넘긴 정렬(최신순)대로 돌려준다")
    void findEarningsByMember() {
        // given
        PointEarning canceled = PointEarning.pending(MEMBER_ID, ORDER_ID_BASE + 3L, 100L);
        canceled.cancel();
        PointEarning expired = completed(4L, 100L, TODAY.minusYears(2));
        expired.expire();
        saveAll(PointEarning.pending(MEMBER_ID, ORDER_ID_BASE + 1L, 100L),
            completed(2L, 100L, TODAY),
            canceled,
            expired,
            completedFor(MEMBER_ID + 1, 5L, 100L, TODAY));

        // when
        Page<PointEarning> page = pointEarningRepository.findByMemberId(MEMBER_ID, PageRequest.of(0, 10,
            Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));

        // then — 같은 순간 저장돼 createdAt이 같아도 id가 큰 것이 먼저다
        assertThat(page.getTotalElements()).isEqualTo(4);
        assertThat(page.getContent()).extracting(PointEarning::getId)
            .isSortedAccordingTo((x, y) -> Long.compare(y, x));
    }

    @Test
    @DisplayName("이력은 회원 조건이 항상 붙어 주문 ID로 좁혀도 다른 회원의 이력은 나오지 않는다")
    void findHistoryByMemberAndOrder() {
        // given — 주문1 이력 2건(적립 예정·확정), 주문2 이력 1건, 다른 회원 주문3 이력 1건
        PointEarning first = PointEarning.pending(MEMBER_ID, ORDER_ID_BASE + 1L, 100L);
        pointEarningRepository.save(first);
        pointHistoryRepository.save(PointHistory.earnPending(first));
        first.complete(NOW);
        pointHistoryRepository.save(PointHistory.earnCompleted(first));
        PointEarning second = completed(2L, 200L, TODAY);
        PointEarning others = completedFor(MEMBER_ID + 1, 3L, 300L, TODAY);
        saveAll(second, others);
        pointHistoryRepository.save(PointHistory.earnCompleted(reload(second)));
        pointHistoryRepository.save(PointHistory.earnCompleted(reload(others)));
        em.flush();
        em.clear();
        PageRequest latest = PageRequest.of(0, 10, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));

        // when
        Page<PointHistory> all = pointHistoryRepository.findByMemberId(MEMBER_ID, latest);
        Page<PointHistory> firstOrder = pointHistoryRepository.findByMemberIdAndOrderId(
            MEMBER_ID, ORDER_ID_BASE + 1L, latest);
        Page<PointHistory> othersOrder = pointHistoryRepository.findByMemberIdAndOrderId(
            MEMBER_ID, ORDER_ID_BASE + 3L, latest);

        // then
        assertThat(all.getContent()).hasSize(3).allMatch(history -> MEMBER_ID.equals(history.getMemberId()));
        assertThat(firstOrder.getContent()).extracting(PointHistory::getType)
            .containsExactly(PointHistoryType.EARN_COMPLETED, PointHistoryType.EARN_PENDING);
        assertThat(othersOrder.getContent()).isEmpty();
    }

    // 적립 확정일로부터 1년 뒤가 만료일이다
    private static PointEarning completed(Long orderId, long amount, LocalDate confirmedOn) {
        PointEarning earning = PointEarning.pending(MEMBER_ID, ORDER_ID_BASE + orderId, amount);
        earning.complete(confirmedOn.atTime(10, 0));
        return earning;
    }

    private static PointEarning completedFor(long memberId, long orderId, long amount, LocalDate confirmedOn) {
        PointEarning earning = PointEarning.pending(memberId, ORDER_ID_BASE + orderId, amount);
        earning.complete(confirmedOn.atTime(10, 0));
        return earning;
    }

    // 값만 조회했다면 영속성 컨텍스트에 올라간 엔티티가 없어야 한다
    private int managedEntityCount() {
        return em.unwrap(SessionImplementor.class).getPersistenceContextInternal().getNumberOfManagedEntities();
    }

    private PointEarning reload(PointEarning earning) {
        return pointEarningRepository.findById(earning.getId()).orElseThrow();
    }

    private void saveAll(PointEarning... earnings) {
        for (PointEarning earning : earnings) {
            pointEarningRepository.save(earning);
        }
        em.flush();
        em.clear();
    }
}
