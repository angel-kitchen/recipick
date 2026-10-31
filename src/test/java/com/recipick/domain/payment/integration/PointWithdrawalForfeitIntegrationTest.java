package com.recipick.domain.payment.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.recipick.IntegrationTestSupport;
import com.recipick.domain.payment.entity.MemberPoint;
import com.recipick.domain.payment.entity.PointEarning;
import com.recipick.domain.payment.entity.PointUsage;
import com.recipick.domain.payment.exception.PaymentErrorCode;
import com.recipick.domain.payment.repository.MemberPointRepository;
import com.recipick.domain.payment.repository.PointEarningRepository;
import com.recipick.domain.payment.repository.PointUsageRepository;
import com.recipick.domain.payment.service.PointCommandService;
import com.recipick.global.exception.BusinessException;
import java.time.Clock;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

// 탈퇴 정산의 포인트 판정·소멸을 실제 MySQL 위에서 본다. 회원 잠금 트랜잭션 안에서 불리는 계약(MANDATORY)이라 테스트가
// 바깥 트랜잭션을 열어 직접 부르고, 거절되면 그 트랜잭션의 변경(지연 만료 포함)이 함께 롤백되는지까지 확인한다.
// 스레드마다 독립 트랜잭션으로 커밋해야 실제 상태가 남으므로 테스트 트랜잭션을 쓰지 않고 끝나면 직접 지운다.
// 주문 행은 포인트 테이블과 외래키로 묶여 있지 않아 주문 ID만 쓴다. 만료 건은 어제, 유효 건은 1년 뒤 만료로 만든다
class PointWithdrawalForfeitIntegrationTest extends IntegrationTestSupport {

    private static final Long MEMBER_ID = 9_600_001L;
    private static final long ORDER_ID_BASE = 9_600_100L;

    @Autowired
    PointCommandService pointCommandService;

    @Autowired
    MemberPointRepository memberPointRepository;

    @Autowired
    PointEarningRepository pointEarningRepository;

    @Autowired
    PointUsageRepository pointUsageRepository;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    Clock clock;

    // point_earning.order_id가 UNIQUE라 적립 건마다 다른 주문 ID를 쓴다
    private long orderSequence;

    @BeforeEach
    void resetOrderSequence() {
        orderSequence = ORDER_ID_BASE;
    }

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
    @DisplayName("확인 잔액이 실제 잔액과 같으면 남은 포인트를 전부 소멸시키고 FORFEITED·WITHDRAWAL_FORFEITED로 남긴다 — 자연 만료와 구분된다")
    void forfeitsWholeBalance() {
        // given — 유효 1,000P
        Long lotId = saveCompletedLot(1_000L, LocalDateTime.now(clock).minusDays(1));
        saveMemberPoint(1_000L);

        // when
        forfeit(1_000L);

        // then
        assertThat(statusOf(lotId)).isEqualTo("FORFEITED");
        assertThat(remainingOf(lotId)).isZero();
        assertThat(balance()).isZero();
        assertThat(historyCount("WITHDRAWAL_FORFEITED")).isEqualTo(1L);
        assertThat(historyAmount("WITHDRAWAL_FORFEITED")).isEqualTo(1_000L);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM point_history WHERE member_id = ? AND type = 'WITHDRAWAL_FORFEITED' "
                + "AND order_id IS NULL",
            Long.class, MEMBER_ID)).isEqualTo(1L);
        assertThat(historyCount("EXPIRED")).isZero();
    }

    @Test
    @DisplayName("적립 건이 여러 개면 모두 소멸시키고 이력은 적립 건마다 남긴다")
    void forfeitsEveryUsableLot() {
        // given — 600P + 400P
        LocalDateTime now = LocalDateTime.now(clock);
        Long first = saveCompletedLot(600L, now.minusDays(30));
        Long second = saveCompletedLot(400L, now.minusDays(1));
        saveMemberPoint(1_000L);

        // when
        forfeit(1_000L);

        // then
        assertThat(statusOf(first)).isEqualTo("FORFEITED");
        assertThat(statusOf(second)).isEqualTo("FORFEITED");
        assertThat(balance()).isZero();
        assertThat(historyCount("WITHDRAWAL_FORFEITED")).isEqualTo(2L);
        assertThat(historyAmount("WITHDRAWAL_FORFEITED")).isEqualTo(1_000L);
    }

    @Test
    @DisplayName("만료일이 지난 적립 건은 먼저 소멸(EXPIRED)시키고, 남은 유효 잔액으로 확인 잔액과 비교한다")
    void expiresStaleLotsBeforeComparing() {
        // given — 어제 만료 600P + 유효 400P, 잔액 1,000P. 재안내 잔액 조회는 유효기간을 반영하므로 회원은 400을 확인했다
        LocalDateTime now = LocalDateTime.now(clock);
        Long stale = saveCompletedLot(600L, now.minusYears(1).minusDays(1));
        Long valid = saveCompletedLot(400L, now.minusDays(1));
        saveMemberPoint(1_000L);

        // when
        forfeit(400L);

        // then
        assertThat(statusOf(stale)).isEqualTo("EXPIRED");
        assertThat(statusOf(valid)).isEqualTo("FORFEITED");
        assertThat(remainingOf(stale)).isZero();
        assertThat(remainingOf(valid)).isZero();
        assertThat(balance()).isZero();
        assertThat(historyCount("EXPIRED")).isEqualTo(1L);
        assertThat(historyAmount("EXPIRED")).isEqualTo(600L);
        assertThat(historyCount("WITHDRAWAL_FORFEITED")).isEqualTo(1L);
        assertThat(historyAmount("WITHDRAWAL_FORFEITED")).isEqualTo(400L);
    }

    @Test
    @DisplayName("잔액이 0이면 소멸할 것이 없어 통과하고 이력도 상태 변경도 없다 — 다 쓴 적립 건은 COMPLETED로 남는다")
    void passesWithZeroBalanceWithoutHistory() {
        // given — 1,000P를 전부 써서 확정(CONFIRMED)했다
        Long lotId = saveCompletedLot(1_000L, LocalDateTime.now(clock).minusDays(1));
        saveMemberPoint(1_000L);
        reserve(lotId, 1_000L, true);
        long historiesBefore = historyCount(null);

        // when
        assertThatCode(() -> forfeit(0L)).doesNotThrowAnyException();

        // then
        assertThat(statusOf(lotId)).isEqualTo("COMPLETED");
        assertThat(remainingOf(lotId)).isZero();
        assertThat(balance()).isZero();
        assertThat(historyCount(null)).isEqualTo(historiesBefore);
        assertThat(historyCount("WITHDRAWAL_FORFEITED")).isZero();
    }

    @Test
    @DisplayName("확인 잔액이 실제 잔액과 다르면 POINT-002로 거절하고, 같은 트랜잭션의 지연 만료까지 모두 롤백한다")
    void rejectsMismatchAndRollsBackLazyExpiration() {
        // given — 어제 만료 600P + 유효 400P. 회원 화면은 만료 전 잔액 1,000을 보여줬다
        LocalDateTime now = LocalDateTime.now(clock);
        Long stale = saveCompletedLot(600L, now.minusYears(1).minusDays(1));
        Long valid = saveCompletedLot(400L, now.minusDays(1));
        saveMemberPoint(1_000L);

        // when
        Throwable thrown = catchThrowable(() -> forfeit(1_000L));

        // then — 거절 자체도, 거절 직전에 한 지연 만료도 DB에 남지 않는다
        assertBusinessError(thrown, PaymentErrorCode.POINT_BALANCE_MISMATCH);
        assertThat(statusOf(stale)).isEqualTo("COMPLETED");
        assertThat(remainingOf(stale)).isEqualTo(600L);
        assertThat(statusOf(valid)).isEqualTo("COMPLETED");
        assertThat(balance()).isEqualTo(1_000L);
        assertThat(historyCount("EXPIRED")).isZero();
        assertThat(historyCount("WITHDRAWAL_FORFEITED")).isZero();
    }

    @Test
    @DisplayName("적립 예정(PENDING)이 있으면 POINT-003으로 탈퇴를 막는다 — 배송완료 뒤 확정될 돈이 남아 있다")
    void rejectsPendingEarning() {
        // given
        Long lotId = saveCompletedLot(1_000L, LocalDateTime.now(clock).minusDays(1));
        savePendingLot(100L);
        saveMemberPoint(1_000L);

        // when
        Throwable thrown = catchThrowable(() -> forfeit(1_000L));

        // then
        assertBusinessError(thrown, PaymentErrorCode.POINT_UNSETTLED_FOR_WITHDRAWAL);
        assertThat(statusOf(lotId)).isEqualTo("COMPLETED");
        assertThat(balance()).isEqualTo(1_000L);
        assertThat(historyCount("WITHDRAWAL_FORFEITED")).isZero();
    }

    @Test
    @DisplayName("사용 예약(RESERVED)이 있으면 POINT-003으로 탈퇴를 막는다 — 결제 결과에 따라 되돌아올 돈이 남아 있다")
    void rejectsReservedUsage() {
        // given — 1,000P 중 300P를 예약(결제 대기)
        Long lotId = saveCompletedLot(1_000L, LocalDateTime.now(clock).minusDays(1));
        saveMemberPoint(1_000L);
        reserve(lotId, 300L, false);

        // when
        Throwable thrown = catchThrowable(() -> forfeit(700L));

        // then
        assertBusinessError(thrown, PaymentErrorCode.POINT_UNSETTLED_FOR_WITHDRAWAL);
        assertThat(statusOf(lotId)).isEqualTo("COMPLETED");
        assertThat(remainingOf(lotId)).isEqualTo(700L);
        assertThat(balance()).isEqualTo(700L);
    }

    @Test
    @DisplayName("확정된 사용(CONFIRMED)은 차단 사유가 아니다 — 남은 잔량만 소멸시키고 사용 내역은 그대로 둔다")
    void ignoresConfirmedUsage() {
        // given — 1,000P 중 300P를 써서 결제까지 끝났다
        Long lotId = saveCompletedLot(1_000L, LocalDateTime.now(clock).minusDays(1));
        saveMemberPoint(1_000L);
        Long usageId = reserve(lotId, 300L, true);

        // when
        forfeit(700L);

        // then
        assertThat(statusOf(lotId)).isEqualTo("FORFEITED");
        assertThat(remainingOf(lotId)).isZero();
        assertThat(balance()).isZero();
        assertThat(historyAmount("WITHDRAWAL_FORFEITED")).isEqualTo(700L);
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM point_usage WHERE id = ?", String.class, usageId))
            .isEqualTo("CONFIRMED");
    }

    @Test
    @DisplayName("잔액과 사용 가능 적립 건 합계가 다르면 회원이 고칠 수 없는 원장 오류라 409가 아닌 내부 오류로 던진다")
    void rejectsLedgerMismatchAsInternalError() {
        // given — 적립 건은 1,000P인데 잔액은 900P
        Long lotId = saveCompletedLot(1_000L, LocalDateTime.now(clock).minusDays(1));
        saveMemberPoint(900L);

        // when
        Throwable thrown = catchThrowable(() -> forfeit(900L));

        // then
        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        assertThat(statusOf(lotId)).isEqualTo("COMPLETED");
        assertThat(balance()).isEqualTo(900L);
        assertThat(historyCount("WITHDRAWAL_FORFEITED")).isZero();
    }

    @Test
    @DisplayName("확인 잔액이 음수면 거절한다 — 회원 요청의 @PositiveOrZero가 먼저 막지만 계약으로도 막는다")
    void rejectsNegativeAcknowledgedBalance() {
        // given
        saveMemberPoint(0L);

        // when
        Throwable thrown = catchThrowable(() -> forfeit(-1L));

        // then
        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("바깥 트랜잭션 없이 부르면 거절한다 — 회원 잠금 트랜잭션 안에서만 뜻이 있다")
    void requiresOuterTransaction() {
        // when
        Throwable thrown = catchThrowable(() -> pointCommandService.forfeitForWithdrawal(MEMBER_ID, 0L));

        // then
        assertThat(thrown).isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    @DisplayName("포인트 행이 없는 회원도 적립 예정이 있으면 POINT-003이다 — 포인트를 안 쓴 주문은 행 없이 PENDING이 생긴다")
    void rejectsPendingWithoutMemberPointRow() {
        // given
        savePendingLot(100L);

        // when
        Throwable thrown = catchThrowable(() -> forfeit(0L));

        // then
        assertBusinessError(thrown, PaymentErrorCode.POINT_UNSETTLED_FOR_WITHDRAWAL);
        assertThat(memberPointRows()).isZero();
    }

    @Test
    @DisplayName("포인트 행이 없는 회원도 사용 예약이 남아 있으면 POINT-003이다")
    void rejectsReservedWithoutMemberPointRow() {
        // given — 행이 지워진 뒤 예약만 남은 비정상 데이터. 미정산을 원장 오류보다 먼저 본다
        Long lotId = saveCompletedLot(1_000L, LocalDateTime.now(clock).minusDays(1));
        saveMemberPoint(1_000L);
        reserve(lotId, 300L, false);
        jdbcTemplate.update("DELETE FROM member_point WHERE member_id = ?", MEMBER_ID);

        // when
        Throwable thrown = catchThrowable(() -> forfeit(0L));

        // then
        assertBusinessError(thrown, PaymentErrorCode.POINT_UNSETTLED_FOR_WITHDRAWAL);
    }

    @Test
    @DisplayName("포인트 행이 없는데 잔량이 남은 확정 적립이 있으면 원장 오류다 — 확정 적립은 잔액 증가와 함께만 생긴다")
    void rejectsCompletedRemainingWithoutMemberPointRow() {
        // given
        saveCompletedLot(500L, LocalDateTime.now(clock).minusDays(1));

        // when
        Throwable thrown = catchThrowable(() -> forfeit(0L));

        // then
        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        assertThat(memberPointRows()).isZero();
    }

    @Test
    @DisplayName("포인트 행이 없는 회원이 0이 아닌 잔액을 확인했다고 보내면 POINT-002다")
    void rejectsAcknowledgedBalanceWithoutMemberPointRow() {
        // when
        Throwable thrown = catchThrowable(() -> forfeit(500L));

        // then
        assertBusinessError(thrown, PaymentErrorCode.POINT_BALANCE_MISMATCH);
        assertThat(memberPointRows()).isZero();
    }

    @Test
    @DisplayName("포인트를 가진 적이 없는 회원은 통과하고 행을 새로 만들지 않는다 — 탈퇴 회원에게 빈 행을 남기지 않는다")
    void passesWithoutMemberPointRowAndCreatesNothing() {
        // when
        assertThatCode(() -> forfeit(0L)).doesNotThrowAnyException();

        // then
        assertThat(memberPointRows()).isZero();
        assertThat(historyCount(null)).isZero();
    }

    @Test
    @DisplayName("소멸이 끝난 뒤 다시 불러도(확인 잔액 0) 통과하고 이력이 늘지 않는다")
    void isIdempotentAfterForfeit() {
        // given
        saveCompletedLot(1_000L, LocalDateTime.now(clock).minusDays(1));
        saveMemberPoint(1_000L);
        forfeit(1_000L);

        // when
        assertThatCode(() -> forfeit(0L)).doesNotThrowAnyException();

        // then
        assertThat(balance()).isZero();
        assertThat(historyCount("WITHDRAWAL_FORFEITED")).isEqualTo(1L);
    }

    // ---------- 픽스처 ----------

    private Long saveCompletedLot(long amount, LocalDateTime completedAt) {
        PointEarning lot = PointEarning.pending(MEMBER_ID, nextOrderId(), amount);
        lot.complete(completedAt);
        return pointEarningRepository.save(lot).getId();
    }

    private Long savePendingLot(long amount) {
        return pointEarningRepository.save(PointEarning.pending(MEMBER_ID, nextOrderId(), amount)).getId();
    }

    private void saveMemberPoint(long balance) {
        memberPointRepository.save(MemberPoint.builder().memberId(MEMBER_ID).balance(balance).build());
    }

    // 주문 생성의 예약(+ 결제 성공의 확정)이 할 일을 흉내 낸다. 잔액 차감은 호출자가 saveMemberPoint 값으로 맞춘다
    private Long reserve(Long lotId, long amount, boolean confirm) {
        LocalDateTime now = LocalDateTime.now(clock);
        Long orderId = nextOrderId();
        return transactionTemplate.execute(status -> {
            PointEarning lot = pointEarningRepository.findById(lotId).orElseThrow();
            lot.use(amount);
            PointUsage usage = pointUsageRepository.save(PointUsage.reserve(lot, orderId, amount, now));
            if (confirm) {
                usage.confirm(now);
            }
            MemberPoint memberPoint = memberPointRepository.findByMemberIdForUpdate(MEMBER_ID).orElseThrow();
            memberPoint.decrease(amount);
            return usage.getId();
        });
    }

    private long nextOrderId() {
        return orderSequence++;
    }

    // ---------- 실행 ----------

    // 회원 탈퇴 트랜잭션이 하는 일 중 포인트 부분만 바깥 트랜잭션을 열어 부른다
    private void forfeit(long acknowledgedBalance) {
        transactionTemplate.executeWithoutResult(
            status -> pointCommandService.forfeitForWithdrawal(MEMBER_ID, acknowledgedBalance));
    }

    private static void assertBusinessError(Throwable thrown, PaymentErrorCode expected) {
        assertThat(thrown).isInstanceOfSatisfying(BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(expected));
    }

    // ---------- 조회 ----------

    private String statusOf(Long lotId) {
        return jdbcTemplate.queryForObject("SELECT status FROM point_earning WHERE id = ?", String.class, lotId);
    }

    private long remainingOf(Long lotId) {
        return jdbcTemplate.queryForObject("SELECT remaining_amount FROM point_earning WHERE id = ?",
            Long.class, lotId);
    }

    private long balance() {
        return jdbcTemplate.queryForObject("SELECT balance FROM member_point WHERE member_id = ?",
            Long.class, MEMBER_ID);
    }

    private long memberPointRows() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM member_point WHERE member_id = ?",
            Long.class, MEMBER_ID);
    }

    // type이 null이면 전체 이력 수
    private long historyCount(String type) {
        if (type == null) {
            return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM point_history WHERE member_id = ?",
                Long.class, MEMBER_ID);
        }
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM point_history WHERE member_id = ? AND type = ?",
            Long.class, MEMBER_ID, type);
    }

    private long historyAmount(String type) {
        return jdbcTemplate.queryForObject(
            "SELECT COALESCE(SUM(amount), 0) FROM point_history WHERE member_id = ? AND type = ?",
            Long.class, MEMBER_ID, type);
    }
}
