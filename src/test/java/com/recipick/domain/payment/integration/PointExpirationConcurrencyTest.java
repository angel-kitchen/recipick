package com.recipick.domain.payment.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.recipick.IntegrationTestSupport;
import com.recipick.domain.payment.entity.MemberPoint;
import com.recipick.domain.payment.entity.PointEarning;
import com.recipick.domain.payment.repository.MemberPointRepository;
import com.recipick.domain.payment.repository.PointEarningRepository;
import com.recipick.domain.payment.service.PointCommandService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

// 만료 배치가 포인트 사용·다른 서버의 만료 배치와 실제로 겹쳐도 MEMBER_POINT 잠금으로 줄을 서서 한 번만 반영되는지 본다.
// 스레드마다 독립 트랜잭션으로 커밋해야 실제 경쟁이 되므로 테스트 트랜잭션을 쓰지 않고 끝나면 직접 지운다.
// 주문 행은 포인트 테이블과 외래키로 묶여 있지 않아 주문 ID만 쓴다. 만료 건은 어제, 유효 건은 1년 뒤 만료로 만든다
class PointExpirationConcurrencyTest extends IntegrationTestSupport {

    private static final Long MEMBER_ID = 9_500_001L;
    private static final Long STALE_ORDER_ID = 9_500_100L;
    private static final Long VALID_ORDER_ID = 9_500_101L;
    private static final Long USING_ORDER_ID = 9_500_102L;

    @Autowired
    PointCommandService pointCommandService;

    @Autowired
    MemberPointRepository memberPointRepository;

    @Autowired
    PointEarningRepository pointEarningRepository;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    Clock clock;

    // 외래키 역순(이력 → 사용 → 적립 → 회원 포인트)으로 지운다
    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM point_history WHERE member_id = ?", MEMBER_ID);
        jdbcTemplate.update("DELETE pu FROM point_usage pu JOIN point_earning pe ON pu.point_earning_id = pe.id "
            + "WHERE pe.member_id = ?", MEMBER_ID);
        jdbcTemplate.update("DELETE FROM point_earning WHERE member_id = ?", MEMBER_ID);
        jdbcTemplate.update("DELETE FROM member_point WHERE member_id = ?", MEMBER_ID);
    }

    @Test
    @DisplayName("만료 배치와 포인트 사용이 동시에 와도 만료 포인트는 쓰이지 않고 소멸 이력은 한 번, 잔액은 음수 없이 0이 된다")
    void expirationAndReservationAreSerialized() throws Exception {
        // given — 어제 만료 100P, 유효 100P, 잔액 200P
        LocalDate today = LocalDate.now(clock);
        Long[] lotIds = holdPoints(100L, 100L);
        Long staleId = lotIds[0];
        Long validId = lotIds[1];

        // when — 어느 쪽이 먼저 잠그든 사용 쪽의 지연 만료나 배치가 만료분을 먼저 소멸시킨다
        List<Object> results = runConcurrently(List.of(
            () -> pointCommandService.expireMember(MEMBER_ID, today),
            () -> {
                transactionTemplate.executeWithoutResult(
                    status -> pointCommandService.reserveUsage(MEMBER_ID, USING_ORDER_ID, 100L));
                return "RESERVED";
            }));

        // then
        assertThat(results.get(0)).isIn(100L, 0L);
        assertThat(results.get(1)).isEqualTo("RESERVED");
        assertThat(queryString("SELECT status FROM point_earning WHERE id = ?", staleId)).isEqualTo("EXPIRED");
        assertThat(queryLong("SELECT remaining_amount FROM point_earning WHERE id = ?", staleId)).isZero();
        assertThat(queryLong("SELECT remaining_amount FROM point_earning WHERE id = ?", validId)).isZero();
        assertThat(queryLong("SELECT balance FROM member_point WHERE member_id = ?", MEMBER_ID)).isZero();
        // 사용은 유효 건에서만 100P 한 건이다 — 만료 건에서 빠져나간 포인트는 없다
        assertThat(queryLong("SELECT COUNT(*) FROM point_usage WHERE order_id = ?", USING_ORDER_ID)).isEqualTo(1L);
        assertThat(queryLong("SELECT point_earning_id FROM point_usage WHERE order_id = ?", USING_ORDER_ID))
            .isEqualTo(validId);
        assertThat(queryLong("SELECT amount FROM point_usage WHERE order_id = ?", USING_ORDER_ID)).isEqualTo(100L);
        assertThat(historyCount("EXPIRED")).isEqualTo(1L);
        assertThat(historyCount("USE_RESERVED")).isEqualTo(1L);
    }

    @Test
    @DisplayName("두 서버의 만료 배치가 같은 회원을 동시에 처리해도 한쪽만 소멸시키고 다른 쪽은 0을 돌려준다 — 분산 락 없이 멱등이다")
    void duplicateExpirationIsIdempotent() throws Exception {
        // given — 어제 만료 600P, 유효 400P, 잔액 1,000P
        LocalDate today = LocalDate.now(clock);
        Long[] lotIds = holdPoints(600L, 400L);

        // when
        List<Object> results = runConcurrently(List.of(
            () -> pointCommandService.expireMember(MEMBER_ID, today),
            () -> pointCommandService.expireMember(MEMBER_ID, today)));

        // then
        assertThat(results).containsExactlyInAnyOrder(600L, 0L);
        assertThat(queryString("SELECT status FROM point_earning WHERE id = ?", lotIds[0])).isEqualTo("EXPIRED");
        assertThat(queryLong("SELECT remaining_amount FROM point_earning WHERE id = ?", lotIds[1])).isEqualTo(400L);
        assertThat(queryLong("SELECT balance FROM member_point WHERE member_id = ?", MEMBER_ID)).isEqualTo(400L);
        assertThat(historyCount("EXPIRED")).isEqualTo(1L);
    }

    // MEMBER_POINT 행 보장은 잠그지 않는 존재 확인 뒤 없을 때만 INSERT한다. 두 트랜잭션이 모두 "없음"을 본 뒤에야 같이
    // 출발시켜 존재 확인과 INSERT 사이의 경합을 일부러 만든다. 늦은 쪽의 INSERT는 member_id UNIQUE에서 앞선 커밋을
    // 기다린 뒤 아무것도 바꾸지 않아야 한다
    @Test
    @DisplayName("포인트 행이 없는 회원을 두 트랜잭션이 '없음'으로 본 뒤 동시에 만들어도 행은 하나이고 잔액은 0이다")
    void firstRowIsCreatedOnceUnderContention() throws Exception {
        // given — 행이 없는 회원. 둘 다 존재 확인을 마칠 때까지 서로 기다린다
        LocalDate today = LocalDate.now(clock);
        CountDownLatch bothSawMissing = new CountDownLatch(2);

        // when
        List<Object> results = runConcurrently(List.of(
            createRowThenExpire(today, bothSawMissing),
            createRowThenExpire(today, bothSawMissing)));

        // then — 소멸할 포인트가 없어 둘 다 0이고, 행은 하나뿐이다
        assertThat(results).containsExactly(0L, 0L);
        assertThat(queryLong("SELECT COUNT(*) FROM member_point WHERE member_id = ?", MEMBER_ID)).isEqualTo(1L);
        assertThat(queryLong("SELECT balance FROM member_point WHERE member_id = ?", MEMBER_ID)).isZero();
    }

    // 한 트랜잭션 안에서 존재 확인 → 상대도 "없음"을 볼 때까지 대기 → expireMember(행 보장·잠금·소멸 0건)
    private Callable<Object> createRowThenExpire(LocalDate today, CountDownLatch bothSawMissing) {
        return () -> transactionTemplate.execute(status -> {
            assertThat(memberPointRepository.existsByMemberId(MEMBER_ID)).isFalse();
            bothSawMissing.countDown();
            awaitOrFail(bothSawMissing);
            return pointCommandService.expireMember(MEMBER_ID, today);
        });
    }

    // 상대 트랜잭션이 15초 안에 오지 않으면 멈추지 않고 실패로 끝낸다
    private static void awaitOrFail(CountDownLatch latch) {
        try {
            if (!latch.await(15, TimeUnit.SECONDS)) {
                throw new IllegalStateException("15초 안에 상대 트랜잭션의 존재 확인이 끝나지 않았습니다.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("존재 확인을 기다리다 중단됐습니다.", e);
        }
    }

    // 만료 건(어제 만료)과 유효 건(1년 뒤 만료)을 만들고 잔액을 두 잔량의 합으로 맞춘다
    private Long[] holdPoints(long staleAmount, long validAmount) {
        LocalDateTime now = LocalDateTime.now(clock);
        return transactionTemplate.execute(status -> {
            PointEarning stale = PointEarning.pending(MEMBER_ID, STALE_ORDER_ID, staleAmount);
            stale.complete(now.minusYears(1).minusDays(1));
            PointEarning valid = PointEarning.pending(MEMBER_ID, VALID_ORDER_ID, validAmount);
            valid.complete(now.minusDays(1));
            pointEarningRepository.save(stale);
            pointEarningRepository.save(valid);
            memberPointRepository.save(
                MemberPoint.builder().memberId(MEMBER_ID).balance(staleAmount + validAmount).build());
            return new Long[] {stale.getId(), valid.getId()};
        });
    }

    // 모든 스레드가 준비된 뒤 한 번에 출발시킨다. 결과는 넘긴 작업 순서대로 돌려준다.
    // 락 대기 초과(5초)보다 넉넉히 기다리고, 예상 밖 예외는 Future.get이 그대로 던진다
    private List<Object> runConcurrently(List<Callable<Object>> tasks) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        try {
            CountDownLatch ready = new CountDownLatch(tasks.size());
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Object>> futures = new ArrayList<>();
            for (Callable<Object> task : tasks) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return task.call();
                }));
            }
            ready.await();
            start.countDown();
            List<Object> results = new ArrayList<>();
            for (Future<Object> future : futures) {
                results.add(future.get(15, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    private long historyCount(String type) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM point_history WHERE member_id = ? AND type = ?",
            Long.class, MEMBER_ID, type);
    }

    private long queryLong(String sql, Object arg) {
        return jdbcTemplate.queryForObject(sql, Long.class, arg);
    }

    private String queryString(String sql, Object arg) {
        return jdbcTemplate.queryForObject(sql, String.class, arg);
    }
}
