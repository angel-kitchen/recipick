package com.recipick.domain.payment.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.recipick.IntegrationTestSupport;
import com.recipick.domain.payment.entity.MemberPoint;
import com.recipick.domain.payment.entity.PointEarning;
import com.recipick.domain.payment.exception.PaymentErrorCode;
import com.recipick.domain.payment.repository.MemberPointRepository;
import com.recipick.domain.payment.repository.PointEarningRepository;
import com.recipick.domain.payment.service.PointCommandService;
import com.recipick.global.exception.BusinessException;
import java.time.Clock;
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

// 같은 회원의 포인트 예약이 실제로 동시에 들어와도 MEMBER_POINT → POINT_EARNING 잠금 체계가 이중 사용을 막는지 본다.
// 다른 회원끼리는 MEMBER_POINT에서 줄을 서지 않으므로, 커밋 전인 예약이 다른 회원 예약을 막지 않는지도 본다.
// 스레드마다 독립 트랜잭션으로 커밋해야 실제 경쟁이 되므로 테스트 트랜잭션을 쓰지 않고 끝나면 직접 지운다.
// 주문 행은 포인트 테이블과 외래키로 묶여 있지 않아 주문 ID만 쓴다. 만료일이 1년 뒤라 실제 시계를 써도 날짜 경계가 없다
class PointReservationConcurrencyTest extends IntegrationTestSupport {

    private static final Long MEMBER_ID = 9_400_001L;
    private static final Long LOT_ORDER_ID = 9_400_100L;
    private static final Long ORDER_A = 9_400_101L;
    private static final Long ORDER_B = 9_400_102L;
    // 다른 회원 예약 검증용. 회원 ID를 이어 붙여 사이 회원의 적립 건이 두 회원의 적립 건 사이에 오게 한다
    private static final Long HOLDING_MEMBER_ID = 9_400_201L;
    private static final Long BETWEEN_MEMBER_ID = 9_400_202L;
    private static final Long NEXT_MEMBER_ID = 9_400_203L;
    private static final Long HOLDING_ORDER_ID = 9_400_301L;
    private static final Long NEXT_ORDER_ID = 9_400_302L;
    private static final List<Long> MEMBER_IDS =
        List.of(MEMBER_ID, HOLDING_MEMBER_ID, BETWEEN_MEMBER_ID, NEXT_MEMBER_ID);
    private static final long HELD_POINT = 1_000L;
    private static final long USED_POINT = 700L;
    // 락 대기 초과(5초)보다 짧다. 막히지 않았다면 예약 한 건은 이보다 훨씬 빨리 끝난다
    private static final long NO_WAIT_MILLIS = 3_000L;
    private static final String SUCCESS = "SUCCESS";

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
        for (Long memberId : MEMBER_IDS) {
            jdbcTemplate.update("DELETE FROM point_history WHERE member_id = ?", memberId);
            jdbcTemplate.update("DELETE pu FROM point_usage pu JOIN point_earning pe ON pu.point_earning_id = pe.id "
                + "WHERE pe.member_id = ?", memberId);
            jdbcTemplate.update("DELETE FROM point_earning WHERE member_id = ?", memberId);
            jdbcTemplate.update("DELETE FROM member_point WHERE member_id = ?", memberId);
        }
    }

    @Test
    @DisplayName("1,000P 회원의 두 주문이 동시에 700P씩 예약하면 한 건만 성공하고 다른 건은 POINT-001이며 잔액과 적립 건 잔량이 모두 300P로 맞는다")
    void onlyOneReservationWinsWithinBalance() throws Exception {
        // given
        transactionTemplate.executeWithoutResult(status -> {
            PointEarning lot = PointEarning.pending(MEMBER_ID, LOT_ORDER_ID, HELD_POINT);
            lot.complete(LocalDateTime.now(clock).minusDays(1));
            pointEarningRepository.save(lot);
            memberPointRepository.save(MemberPoint.builder().memberId(MEMBER_ID).balance(HELD_POINT).build());
        });

        // when
        List<Object> results = runConcurrently(List.of(reserve(ORDER_A), reserve(ORDER_B)));

        // then — 성공 한 건, 포인트 부족 한 건
        assertThat(results).containsExactlyInAnyOrder(SUCCESS, PaymentErrorCode.INSUFFICIENT_POINT_BALANCE);
        // 회원 잔액(집계)과 적립 건 잔량(원장)이 같은 값으로 맞는다
        assertThat(queryLong("SELECT balance FROM member_point WHERE member_id = ?")).isEqualTo(300L);
        assertThat(queryLong("SELECT remaining_amount FROM point_earning WHERE member_id = ?")).isEqualTo(300L);
        assertThat(queryLong("SELECT COALESCE(SUM(pu.amount), 0) FROM point_usage pu "
            + "JOIN point_earning pe ON pu.point_earning_id = pe.id WHERE pe.member_id = ?")).isEqualTo(USED_POINT);
        assertThat(queryLong("SELECT COALESCE(SUM(amount), 0) FROM point_history "
            + "WHERE member_id = ? AND type = 'USE_RESERVED'")).isEqualTo(USED_POINT);
        // 금액 합계뿐 아니라 이긴 주문의 예약 한 건만 커밋됐다
        assertThat(queryLong("SELECT COUNT(*) FROM point_usage pu "
            + "JOIN point_earning pe ON pu.point_earning_id = pe.id WHERE pe.member_id = ?")).isEqualTo(1L);
        assertThat(queryLong("SELECT COUNT(*) FROM point_history "
            + "WHERE member_id = ? AND type = 'USE_RESERVED'")).isEqualTo(1L);
    }

    @Test
    @DisplayName("적립 건이 있는 서로 다른 회원의 예약은 커밋 전인 상대 예약의 MEMBER_POINT 행 보장·같은 주문 확인 잠금에 기다리지 않는다")
    void reservationDoesNotWaitForOtherMembersUncommittedReservation() throws Exception {
        // given — 사이 회원의 적립 건을 두 회원 사이에 둬 적립 건 인덱스의 이웃 행 잠금이 끼어들지 않게 한다.
        // 그래야 이 테스트가 point_usage의 같은 주문 확인 하나만 본다
        holdPoint(HOLDING_MEMBER_ID, 9_400_211L);
        holdPoint(BETWEEN_MEMBER_ID, 9_400_212L);
        holdPoint(NEXT_MEMBER_ID, 9_400_213L);
        CountDownLatch reserved = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            // 먼저 예약한 트랜잭션을 커밋하지 않고 붙잡아 둔다
            Future<?> holding = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                pointCommandService.reserveUsage(HOLDING_MEMBER_ID, HOLDING_ORDER_ID, USED_POINT);
                reserved.countDown();
                awaitSignal(release);
            }));
            awaitSignal(reserved);

            // when — 다른 회원이 바로 다음 주문 ID로 예약한다
            long startedAt = System.nanoTime();
            String result = executor.submit(() -> reserveOrDescribe(NEXT_MEMBER_ID, NEXT_ORDER_ID))
                .get(15, TimeUnit.SECONDS);
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            release.countDown();
            holding.get(15, TimeUnit.SECONDS);

            // then — 기다리지 않고 끝났고, 두 예약이 모두 커밋돼 회원마다 잔액과 적립 건 잔량이 300P로 맞는다
            assertThat(result).isEqualTo(SUCCESS);
            assertThat(elapsedMillis).isLessThan(NO_WAIT_MILLIS);
            for (Long memberId : List.of(HOLDING_MEMBER_ID, NEXT_MEMBER_ID)) {
                assertThat(queryLong("SELECT balance FROM member_point WHERE member_id = ?", memberId))
                    .isEqualTo(300L);
                assertThat(queryLong("SELECT remaining_amount FROM point_earning WHERE member_id = ?", memberId))
                    .isEqualTo(300L);
                assertThat(queryLong("SELECT COUNT(*) FROM point_usage pu "
                    + "JOIN point_earning pe ON pu.point_earning_id = pe.id WHERE pe.member_id = ?", memberId))
                    .isEqualTo(1L);
            }
        } finally {
            // 예외로 끝나도 붙잡아 둔 트랜잭션이 끝난 뒤에 정리가 돌도록 스레드가 멈추기를 기다린다
            release.countDown();
            executor.shutdown();
            if (!executor.awaitTermination(20, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        }
    }

    // 회원 한 명에게 어제 확정된 적립 건 하나와 같은 금액의 잔액을 둔다(잔액 = 적립 건 잔량)
    private void holdPoint(Long memberId, Long lotOrderId) {
        transactionTemplate.executeWithoutResult(status -> {
            PointEarning lot = PointEarning.pending(memberId, lotOrderId, HELD_POINT);
            lot.complete(LocalDateTime.now(clock).minusDays(1));
            pointEarningRepository.save(lot);
            memberPointRepository.save(MemberPoint.builder().memberId(memberId).balance(HELD_POINT).build());
        });
    }

    // 다른 회원의 예약. 락 대기 초과로 실패하면 어느 SQL에서 막혔는지 보이도록 예외 메시지를 결과로 돌려준다
    private String reserveOrDescribe(Long memberId, Long orderId) {
        try {
            transactionTemplate.executeWithoutResult(
                status -> pointCommandService.reserveUsage(memberId, orderId, USED_POINT));
            return SUCCESS;
        } catch (RuntimeException e) {
            return e.getClass().getSimpleName() + ": " + e.getMessage();
        }
    }

    // 신호를 기다린다. 15초 안에 오지 않으면 테스트가 멈추지 않도록 실패로 끝낸다
    private static void awaitSignal(CountDownLatch latch) {
        try {
            if (!latch.await(15, TimeUnit.SECONDS)) {
                throw new IllegalStateException("15초 안에 신호를 받지 못했습니다.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("신호를 기다리다 중단됐습니다.", e);
        }
    }

    // 각자 독립 트랜잭션으로 예약한다. 포인트 부족은 롤백되고 오류 코드를 결과로 돌려준다
    private Callable<Object> reserve(Long orderId) {
        return () -> {
            try {
                transactionTemplate.executeWithoutResult(
                    status -> pointCommandService.reserveUsage(MEMBER_ID, orderId, USED_POINT));
                return SUCCESS;
            } catch (BusinessException e) {
                return e.getErrorCode();
            }
        };
    }

    // 모든 스레드가 준비된 뒤 한 번에 출발시켜 같은 회원의 포인트 행들을 두고 실제로 경쟁하게 만든다.
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

    private long queryLong(String sql) {
        return queryLong(sql, MEMBER_ID);
    }

    private long queryLong(String sql, Long memberId) {
        return jdbcTemplate.queryForObject(sql, Long.class, memberId);
    }
}
