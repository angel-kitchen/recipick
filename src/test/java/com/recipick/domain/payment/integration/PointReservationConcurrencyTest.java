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
// 스레드마다 독립 트랜잭션으로 커밋해야 실제 경쟁이 되므로 테스트 트랜잭션을 쓰지 않고 끝나면 직접 지운다.
// 주문 행은 포인트 테이블과 외래키로 묶여 있지 않아 주문 ID만 쓴다. 만료일이 1년 뒤라 실제 시계를 써도 날짜 경계가 없다
class PointReservationConcurrencyTest extends IntegrationTestSupport {

    private static final Long MEMBER_ID = 9_400_001L;
    private static final Long LOT_ORDER_ID = 9_400_100L;
    private static final Long ORDER_A = 9_400_101L;
    private static final Long ORDER_B = 9_400_102L;
    private static final long HELD_POINT = 1_000L;
    private static final long USED_POINT = 700L;
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
        jdbcTemplate.update("DELETE FROM point_history WHERE member_id = ?", MEMBER_ID);
        jdbcTemplate.update("DELETE pu FROM point_usage pu JOIN point_earning pe ON pu.point_earning_id = pe.id "
            + "WHERE pe.member_id = ?", MEMBER_ID);
        jdbcTemplate.update("DELETE FROM point_earning WHERE member_id = ?", MEMBER_ID);
        jdbcTemplate.update("DELETE FROM member_point WHERE member_id = ?", MEMBER_ID);
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
        return jdbcTemplate.queryForObject(sql, Long.class, MEMBER_ID);
    }
}
