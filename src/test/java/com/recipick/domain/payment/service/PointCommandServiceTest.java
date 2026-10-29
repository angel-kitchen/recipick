package com.recipick.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.tuple;

import com.recipick.IntegrationTestSupport;
import com.recipick.domain.payment.entity.MemberPoint;
import com.recipick.domain.payment.entity.PointEarning;
import com.recipick.domain.payment.entity.PointHistory;
import com.recipick.domain.payment.entity.PointUsage;
import com.recipick.domain.payment.entity.enums.PointEarningStatus;
import com.recipick.domain.payment.entity.enums.PointHistoryType;
import com.recipick.domain.payment.entity.enums.PointUsageStatus;
import com.recipick.domain.payment.exception.PaymentErrorCode;
import com.recipick.domain.payment.repository.MemberPointRepository;
import com.recipick.domain.payment.repository.PointEarningRepository;
import com.recipick.domain.payment.repository.PointHistoryRepository;
import com.recipick.domain.payment.repository.PointUsageRepository;
import com.recipick.global.exception.BusinessException;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// 포인트 정책은 만료일 당일·다음 날 같은 날짜 경계가 업무 규칙이라 시각을 2026-10-28 14:00(KST)로 고정한다.
// 만료일이 지난 포인트의 복원은 시간을 흘려보낼 수 없어, 예약 당시 상태(사용 내역·잔량)를 직접 만들어 둔다
@Transactional
class PointCommandServiceTest extends IntegrationTestSupport {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 28, 14, 0);
    private static final LocalDate TODAY = NOW.toLocalDate();
    private static final Long MEMBER_ID = 9_200_001L;
    private static final long LOT_ORDER_BASE = 9_200_000L;
    private static final Long ORDER_ID = 9_200_100L;
    private static final BigDecimal RATE = new BigDecimal("0.0050");

    // 기본 시계 빈 대신 고정 시계를 우선 주입한다. 목(mock)과 달리 스텁이 풀리는 순간이 없어
    // 같은 컨텍스트의 지연 큐 워커가 매초 시계를 읽어도 null이 나오지 않는다
    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL);
        }
    }

    @Autowired
    PointCommandService pointCommandService;

    @Autowired
    MemberPointRepository memberPointRepository;

    @Autowired
    PointEarningRepository pointEarningRepository;

    @Autowired
    PointUsageRepository pointUsageRepository;

    @Autowired
    PointHistoryRepository pointHistoryRepository;

    @Autowired
    EntityManager em;

    @Test
    @DisplayName("잠금은 id 순(A→B)이지만 사용은 만료가 빠른 B부터 한다")
    void reserveUsesEarlierExpiryFirst() {
        // given — A(id 작음) 12/31 만료 300P, B(id 큼) 11/30 만료 500P
        PointEarning lotA = lot(1L, 300L, LocalDate.of(2026, 12, 31));
        PointEarning lotB = lot(2L, 500L, LocalDate.of(2026, 11, 30));
        holdPoints(lotA, lotB);

        // when
        pointCommandService.reserveUsage(MEMBER_ID, ORDER_ID, 400L);
        flushAndClear();

        // then
        assertThat(lotA.getId()).isLessThan(lotB.getId());
        assertThat(earning(lotB).getRemainingAmount()).isEqualTo(100L);
        assertThat(earning(lotA).getRemainingAmount()).isEqualTo(300L);
        assertThat(usages()).singleElement().satisfies(usage -> {
            assertThat(usage.getPointEarning().getId()).isEqualTo(lotB.getId());
            assertThat(usage.getAmount()).isEqualTo(400L);
            assertThat(usage.getStatus()).isEqualTo(PointUsageStatus.RESERVED);
        });
    }

    @Test
    @DisplayName("한 적립 건으로 모자라면 만료 빠른 순으로 나눠 쓰고 적립 건마다 사용 내역과 이력을 남긴다")
    void reserveSplitsAcrossLots() {
        // given
        PointEarning first = lot(1L, 300L, LocalDate.of(2026, 11, 30));
        PointEarning second = lot(2L, 500L, LocalDate.of(2026, 12, 31));
        holdPoints(first, second);

        // when
        pointCommandService.reserveUsage(MEMBER_ID, ORDER_ID, 700L);
        flushAndClear();

        // then
        assertThat(earning(first).getRemainingAmount()).isZero();
        assertThat(earning(second).getRemainingAmount()).isEqualTo(100L);
        assertThat(usages()).extracting(PointUsage::getAmount).containsExactly(300L, 400L);
        assertThat(histories(PointHistoryType.USE_RESERVED)).extracting(PointHistory::getAmount)
            .containsExactly(300L, 400L);
    }

    @Test
    @DisplayName("예약한 포인트만큼 회원 잔액을 한 번만 줄인다")
    void reserveDecreasesBalanceOnce() {
        // given
        holdPoints(lot(1L, 300L, LocalDate.of(2026, 11, 30)), lot(2L, 500L, LocalDate.of(2026, 12, 31)));

        // when
        pointCommandService.reserveUsage(MEMBER_ID, ORDER_ID, 700L);
        flushAndClear();

        // then
        assertThat(balance()).isEqualTo(100L);
    }

    @Test
    @DisplayName("같은 주문으로 다시 예약하면 전부 RESERVED여도 전부 CONFIRMED여도 아무것도 바꾸지 않고, 금액이 다르면 내부 오류다")
    void reserveAgainIsIdempotent() {
        // given
        holdPoints(lot(1L, 300L, LocalDate.of(2026, 11, 30)), lot(2L, 500L, LocalDate.of(2026, 12, 31)));
        placeOrder(10_000L, 700L);

        // when
        pointCommandService.reserveUsage(MEMBER_ID, ORDER_ID, 700L);
        pointCommandService.confirmUsage(ORDER_ID);
        pointCommandService.reserveUsage(MEMBER_ID, ORDER_ID, 700L);
        Throwable different = catchThrowable(() -> pointCommandService.reserveUsage(MEMBER_ID, ORDER_ID, 800L));
        flushAndClear();

        // then
        assertThat(different).isInstanceOf(IllegalStateException.class);
        assertThat(balance()).isEqualTo(100L);
        assertThat(usages()).hasSize(2).allMatch(usage -> usage.getStatus() == PointUsageStatus.CONFIRMED);
        assertThat(histories(PointHistoryType.USE_RESERVED)).hasSize(2);
    }

    @Test
    @DisplayName("사용 가능 포인트보다 많이 쓰면 POINT-001이고 잔액·적립 건·사용 내역은 그대로다")
    void reserveRejectsInsufficientPoint() {
        // given
        PointEarning lot = lot(1L, 800L, LocalDate.of(2026, 12, 31));
        holdPoints(lot);

        // when
        Throwable thrown = catchThrowable(() -> pointCommandService.reserveUsage(MEMBER_ID, ORDER_ID, 801L));
        flushAndClear();

        // then
        assertThat(thrown).isInstanceOfSatisfying(BusinessException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo(PaymentErrorCode.INSUFFICIENT_POINT_BALANCE));
        assertThat(balance()).isEqualTo(800L);
        assertThat(earning(lot).getRemainingAmount()).isEqualTo(800L);
        assertThat(usages()).isEmpty();
    }

    @Test
    @DisplayName("만료 배치가 아직 처리하지 않은 만료 포인트는 예약 전에 먼저 소멸시키고 남은 유효 포인트만 쓴다")
    void reserveExpiresStaleLotsFirst() {
        // given — 어제 만료 500P, 내년 만료 500P
        PointEarning stale = lot(1L, 500L, TODAY.minusDays(1));
        PointEarning valid = lot(2L, 500L, TODAY.plusYears(1));
        holdPoints(stale, valid);

        // when
        pointCommandService.reserveUsage(MEMBER_ID, ORDER_ID, 300L);
        flushAndClear();

        // then
        assertThat(earning(stale).getStatus()).isEqualTo(PointEarningStatus.EXPIRED);
        assertThat(earning(stale).getRemainingAmount()).isZero();
        assertThat(earning(valid).getRemainingAmount()).isEqualTo(200L);
        assertThat(balance()).isEqualTo(200L);
        assertThat(histories(PointHistoryType.EXPIRED)).singleElement().satisfies(history -> {
            assertThat(history.getAmount()).isEqualTo(500L);
            assertThat(history.getOrderId()).isNull();
        });
    }

    @Test
    @DisplayName("회원 잔액과 사용 가능 적립 건 합계가 다르면 내부 오류이고 아무것도 쓰지 않는다")
    void reserveDetectsBalanceMismatch() {
        // given — 적립 건은 500P인데 잔액은 600P
        PointEarning lot = lot(1L, 500L, LocalDate.of(2026, 12, 31));
        memberPointRepository.save(MemberPoint.builder().memberId(MEMBER_ID).balance(600L).build());
        flushAndClear();

        // when
        Throwable thrown = catchThrowable(() -> pointCommandService.reserveUsage(MEMBER_ID, ORDER_ID, 100L));
        flushAndClear();

        // then
        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        assertThat(earning(lot).getRemainingAmount()).isEqualTo(500L);
        assertThat(usages()).isEmpty();
    }

    @Test
    @DisplayName("적립 예정은 (상품금액 − 사용 포인트) × 적립률을 원 미만 버림한 금액의 PENDING 건이다")
    void createPendingEarning() {
        // when — (10,000 − 700) × 0.005 = 46.5 → 46
        pointCommandService.createPendingEarning(MEMBER_ID, ORDER_ID, 10_000L, 700L, RATE);
        flushAndClear();

        // then
        PointEarning earning = orderEarning();
        assertThat(earning.getStatus()).isEqualTo(PointEarningStatus.PENDING);
        assertThat(earning.getAmount()).isEqualTo(46L);
        assertThat(earning.getRemainingAmount()).isZero();
        assertThat(histories(PointHistoryType.EARN_PENDING)).extracting(PointHistory::getAmount).containsExactly(46L);
    }

    @Test
    @DisplayName("적립액이 0원이어도 적립 건은 만들고 이력은 남기지 않는다")
    void createZeroPendingEarning() {
        // when — 상품금액 전부를 포인트로 냈다
        pointCommandService.createPendingEarning(MEMBER_ID, ORDER_ID, 20_000L, 20_000L, RATE);
        flushAndClear();

        // then
        PointEarning earning = orderEarning();
        assertThat(earning.getStatus()).isEqualTo(PointEarningStatus.PENDING);
        assertThat(earning.getAmount()).isZero();
        assertThat(histories()).isEmpty();
    }

    @Test
    @DisplayName("같은 주문으로 다시 만들면 아무것도 바꾸지 않고, 금액이 다르면 내부 오류다")
    void createPendingTwice() {
        // given
        pointCommandService.createPendingEarning(MEMBER_ID, ORDER_ID, 10_000L, 700L, RATE);

        // when
        pointCommandService.createPendingEarning(MEMBER_ID, ORDER_ID, 10_000L, 700L, RATE);
        Throwable different = catchThrowable(() ->
            pointCommandService.createPendingEarning(MEMBER_ID, ORDER_ID, 20_000L, 700L, RATE));
        flushAndClear();

        // then
        assertThat(different).isInstanceOf(IllegalStateException.class);
        assertThat(pointEarningRepository.findByOrderId(ORDER_ID)).isPresent();
        assertThat(histories(PointHistoryType.EARN_PENDING)).hasSize(1);
    }

    @Test
    @DisplayName("결제 성공으로 사용을 확정하면 CONFIRMED가 되고 잔액은 다시 줄지 않는다")
    void confirmUsage() {
        // given
        holdPoints(lot(1L, 500L, LocalDate.of(2026, 12, 31)));
        placeOrder(10_000L, 300L);

        // when
        pointCommandService.confirmUsage(ORDER_ID);
        flushAndClear();

        // then
        assertThat(usages()).singleElement().satisfies(usage -> {
            assertThat(usage.getStatus()).isEqualTo(PointUsageStatus.CONFIRMED);
            assertThat(usage.getConfirmedAt()).isEqualTo(NOW);
        });
        assertThat(balance()).isEqualTo(200L);
        assertThat(histories(PointHistoryType.USE_CONFIRMED)).extracting(PointHistory::getAmount)
            .containsExactly(300L);
    }

    @Test
    @DisplayName("사용 확정을 두 번 불러도 이력은 한 번만 남는다")
    void confirmUsageTwice() {
        // given
        holdPoints(lot(1L, 500L, LocalDate.of(2026, 12, 31)));
        placeOrder(10_000L, 300L);

        // when
        pointCommandService.confirmUsage(ORDER_ID);
        pointCommandService.confirmUsage(ORDER_ID);
        flushAndClear();

        // then
        assertThat(histories(PointHistoryType.USE_CONFIRMED)).hasSize(1);
        assertThat(balance()).isEqualTo(200L);
    }

    @Test
    @DisplayName("결제 전에 끝난 주문의 예약 포인트를 원래 적립 건과 잔액으로 되돌린다")
    void restoreReservedUsage() {
        // given
        PointEarning lot = lot(1L, 500L, LocalDate.of(2026, 12, 31));
        holdPoints(lot);
        placeOrder(10_000L, 300L);

        // when
        pointCommandService.restoreUsageAndCancelEarning(ORDER_ID);
        flushAndClear();

        // then
        assertThat(usages()).singleElement()
            .satisfies(usage -> assertThat(usage.getStatus()).isEqualTo(PointUsageStatus.RESTORED));
        assertThat(earning(lot).getRemainingAmount()).isEqualTo(500L);
        assertThat(balance()).isEqualTo(500L);
        assertThat(histories(PointHistoryType.RESTORED)).extracting(PointHistory::getAmount).containsExactly(300L);
    }

    @Test
    @DisplayName("결제 후 환불된 주문의 확정 포인트도 되돌리고, 한때 확정됐던 시각은 남긴다")
    void restoreConfirmedUsage() {
        // given
        PointEarning lot = lot(1L, 500L, LocalDate.of(2026, 12, 31));
        holdPoints(lot);
        placeOrder(10_000L, 300L);
        pointCommandService.confirmUsage(ORDER_ID);

        // when
        pointCommandService.restoreUsageAndCancelEarning(ORDER_ID);
        flushAndClear();

        // then
        assertThat(usages()).singleElement().satisfies(usage -> {
            assertThat(usage.getStatus()).isEqualTo(PointUsageStatus.RESTORED);
            assertThat(usage.getConfirmedAt()).isEqualTo(NOW);
        });
        assertThat(earning(lot).getRemainingAmount()).isEqualTo(500L);
        assertThat(balance()).isEqualTo(500L);
    }

    @Test
    @DisplayName("여러 적립 건에서 나눠 쓴 포인트는 각자 원래 적립 건으로 쓴 만큼만 돌아간다")
    void restoreToOriginalLots() {
        // given
        PointEarning first = lot(1L, 300L, LocalDate.of(2026, 11, 30));
        PointEarning second = lot(2L, 500L, LocalDate.of(2026, 12, 31));
        holdPoints(first, second);
        placeOrder(10_000L, 700L);

        // when
        pointCommandService.restoreUsageAndCancelEarning(ORDER_ID);
        flushAndClear();

        // then
        assertThat(earning(first).getRemainingAmount()).isEqualTo(300L);
        assertThat(earning(second).getRemainingAmount()).isEqualTo(500L);
        assertThat(balance()).isEqualTo(800L);
        assertThat(histories(PointHistoryType.RESTORED)).extracting(PointHistory::getAmount)
            .containsExactly(300L, 400L);
    }

    @Test
    @DisplayName("원래 만료일이 지난 포인트는 되돌렸다는 기록 뒤 바로 소멸하고 잔액은 늘지 않는다 — 소멸 금액은 사용분이다")
    void restoreExpiredUsage() {
        // given — 어제 만료된 100P 적립 건을 이 주문이 전부 썼다(잔량 0이라 만료 배치 대상이 아니어서 COMPLETED로 남아 있다)
        PointEarning lot = lot(1L, 100L, TODAY.minusDays(1));
        usedByOrder(lot, 100L);

        // when
        pointCommandService.restoreUsageAndCancelEarning(ORDER_ID);
        flushAndClear();

        // then
        assertThat(earning(lot).getStatus()).isEqualTo(PointEarningStatus.EXPIRED);
        assertThat(earning(lot).getRemainingAmount()).isZero();
        assertThat(balance()).isZero();
        assertThat(histories())
            .extracting(PointHistory::getType, PointHistory::getAmount, PointHistory::getOrderId)
            .containsExactly(
                tuple(PointHistoryType.RESTORED, 100L, ORDER_ID),
                tuple(PointHistoryType.EXPIRED, 100L, ORDER_ID));
    }

    @Test
    @DisplayName("만료일이 지난 적립 건에 남아 있던 잔량도 먼저 소멸시킨 뒤 환불분을 기록·소멸한다")
    void restoreExpiredUsageWithRemaining() {
        // given — 어제 만료된 1,000P 중 400P를 이 주문이 썼고 600P가 남았는데 만료 배치가 아직 돌지 않았다
        PointEarning lot = lot(1L, 1_000L, TODAY.minusDays(1));
        usedByOrder(lot, 400L);

        // when
        pointCommandService.restoreUsageAndCancelEarning(ORDER_ID);
        flushAndClear();

        // then — EXPIRED(기존 잔량) → RESTORED(사용분) → EXPIRED(사용분)
        assertThat(earning(lot).getStatus()).isEqualTo(PointEarningStatus.EXPIRED);
        assertThat(earning(lot).getRemainingAmount()).isZero();
        assertThat(balance()).isZero();
        assertThat(histories())
            .extracting(PointHistory::getType, PointHistory::getAmount, PointHistory::getOrderId)
            .containsExactly(
                tuple(PointHistoryType.EXPIRED, 600L, null),
                tuple(PointHistoryType.RESTORED, 400L, ORDER_ID),
                tuple(PointHistoryType.EXPIRED, 400L, ORDER_ID));
    }

    @Test
    @DisplayName("복원을 세 번 불러도 잔액과 이력은 처음 한 번만 반영된다")
    void restoreTwice() {
        // given
        holdPoints(lot(1L, 500L, LocalDate.of(2026, 12, 31)));
        placeOrder(10_000L, 300L);

        // when
        pointCommandService.restoreUsageAndCancelEarning(ORDER_ID);
        pointCommandService.restoreUsageAndCancelEarning(ORDER_ID);
        pointCommandService.restoreUsageAndCancelEarning(ORDER_ID);
        flushAndClear();

        // then
        assertThat(balance()).isEqualTo(500L);
        assertThat(histories(PointHistoryType.RESTORED)).hasSize(1);
        assertThat(histories(PointHistoryType.EARN_CANCELED)).hasSize(1);
    }

    @Test
    @DisplayName("배송완료로 적립이 확정된 주문은 자동으로 되돌리지 않고 내부 오류이며 아무것도 바뀌지 않는다")
    void rejectRestoreAfterEarningCompleted() {
        // given
        holdPoints(lot(1L, 500L, LocalDate.of(2026, 12, 31)));
        placeOrder(10_000L, 300L);
        pointCommandService.confirmUsage(ORDER_ID);
        pointCommandService.confirmEarning(ORDER_ID);
        flushAndClear();
        long balanceBefore = balance();

        // when
        Throwable thrown = catchThrowable(() -> pointCommandService.restoreUsageAndCancelEarning(ORDER_ID));
        flushAndClear();

        // then
        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        assertThat(balance()).isEqualTo(balanceBefore);
        assertThat(usages()).singleElement()
            .satisfies(usage -> assertThat(usage.getStatus()).isEqualTo(PointUsageStatus.CONFIRMED));
        assertThat(orderEarning().getStatus()).isEqualTo(PointEarningStatus.COMPLETED);
    }

    @Test
    @DisplayName("외부 취소에서 적립 예정 주문은 포인트를 되돌리고 RESTORED이며, 다시 불러도 이미 안전한 상태라 RESTORED만 돌려준다")
    void externalCancelRestoresPendingOrder() {
        // given
        holdPoints(lot(1L, 500L, LocalDate.of(2026, 12, 31)));
        placeOrder(10_000L, 300L);
        pointCommandService.confirmUsage(ORDER_ID);

        // when
        PointRestoreOutcome first = pointCommandService.restoreForExternalCancel(ORDER_ID);
        PointRestoreOutcome second = pointCommandService.restoreForExternalCancel(ORDER_ID);
        flushAndClear();

        // then
        assertThat(first).isEqualTo(PointRestoreOutcome.RESTORED);
        assertThat(second).isEqualTo(PointRestoreOutcome.RESTORED);
        assertThat(balance()).isEqualTo(500L);
        assertThat(usages()).singleElement()
            .satisfies(usage -> assertThat(usage.getStatus()).isEqualTo(PointUsageStatus.RESTORED));
        assertThat(orderEarning().getStatus()).isEqualTo(PointEarningStatus.CANCELED);
        assertThat(histories(PointHistoryType.RESTORED)).hasSize(1);
    }

    @Test
    @DisplayName("외부 취소에서 적립이 확정된 주문은 예외 없이 아무것도 되돌리지 않고 FINALIZED_EARNING_KEPT를 돌려준다")
    void externalCancelKeepsFinalizedEarning() {
        // given
        holdPoints(lot(1L, 500L, LocalDate.of(2026, 12, 31)));
        placeOrder(10_000L, 300L);
        pointCommandService.confirmUsage(ORDER_ID);
        pointCommandService.confirmEarning(ORDER_ID);
        flushAndClear();
        long balanceBefore = balance();

        // when
        PointRestoreOutcome outcome = pointCommandService.restoreForExternalCancel(ORDER_ID);
        flushAndClear();

        // then
        assertThat(outcome).isEqualTo(PointRestoreOutcome.FINALIZED_EARNING_KEPT);
        assertThat(balance()).isEqualTo(balanceBefore);
        assertThat(usages()).singleElement()
            .satisfies(usage -> assertThat(usage.getStatus()).isEqualTo(PointUsageStatus.CONFIRMED));
        assertThat(orderEarning().getStatus()).isEqualTo(PointEarningStatus.COMPLETED);
        assertThat(histories(PointHistoryType.RESTORED)).isEmpty();
    }

    @Test
    @DisplayName("외부 취소에서 적립 건이 없는 1차 주문은 NO_POINT이고 회원 포인트 행도 만들지 않는다")
    void externalCancelWithoutEarningIsNoPoint() {
        // when
        PointRestoreOutcome outcome = pointCommandService.restoreForExternalCancel(ORDER_ID);
        flushAndClear();

        // then
        assertThat(outcome).isEqualTo(PointRestoreOutcome.NO_POINT);
        assertThat(memberPointRepository.findByMemberIdForUpdate(MEMBER_ID)).isEmpty();
        assertThat(histories()).isEmpty();
    }

    @Test
    @DisplayName("주문이 끝나면 그 주문의 적립 예정을 CANCELED로 바꾸고 이력을 남긴다")
    void cancelPendingEarning() {
        // given — (10,000 − 300) × 0.005 = 48.5 → 48
        holdPoints(lot(1L, 500L, LocalDate.of(2026, 12, 31)));
        placeOrder(10_000L, 300L);

        // when
        pointCommandService.restoreUsageAndCancelEarning(ORDER_ID);
        flushAndClear();

        // then
        assertThat(orderEarning().getStatus()).isEqualTo(PointEarningStatus.CANCELED);
        assertThat(histories(PointHistoryType.EARN_CANCELED)).extracting(PointHistory::getAmount)
            .containsExactly(48L);
    }

    @Test
    @DisplayName("배송완료로 적립을 확정하면 잔액이 늘고 확정일로부터 1년 뒤가 만료일이 된다")
    void confirmEarning() {
        // given — 10,000 × 0.005 = 50
        pointCommandService.createPendingEarning(MEMBER_ID, ORDER_ID, 10_000L, 0L, RATE);

        // when
        pointCommandService.confirmEarning(ORDER_ID);
        flushAndClear();

        // then
        PointEarning earning = orderEarning();
        assertThat(earning.getStatus()).isEqualTo(PointEarningStatus.COMPLETED);
        assertThat(earning.getRemainingAmount()).isEqualTo(50L);
        assertThat(earning.getConfirmedAt()).isEqualTo(NOW);
        assertThat(earning.getExpiresOn()).isEqualTo(LocalDate.of(2027, 10, 28));
        assertThat(balance()).isEqualTo(50L);
        assertThat(histories(PointHistoryType.EARN_COMPLETED)).extracting(PointHistory::getAmount)
            .containsExactly(50L);
    }

    @Test
    @DisplayName("적립 확정을 두 번 불러도 포인트는 한 번만 지급된다")
    void confirmEarningTwice() {
        // given
        pointCommandService.createPendingEarning(MEMBER_ID, ORDER_ID, 10_000L, 0L, RATE);

        // when
        pointCommandService.confirmEarning(ORDER_ID);
        pointCommandService.confirmEarning(ORDER_ID);
        flushAndClear();

        // then
        assertThat(balance()).isEqualTo(50L);
        assertThat(histories(PointHistoryType.EARN_COMPLETED)).hasSize(1);
    }

    @Test
    @DisplayName("적립 건이 없는 1차 주문은 확정·복원·적립 확정 모두 아무것도 하지 않는다")
    void legacyOrderWithoutEarningIsNoOp() {
        // when
        pointCommandService.confirmUsage(ORDER_ID);
        pointCommandService.restoreUsageAndCancelEarning(ORDER_ID);
        pointCommandService.confirmEarning(ORDER_ID);
        flushAndClear();

        // then
        assertThat(memberPointRepository.findByMemberIdForUpdate(MEMBER_ID)).isEmpty();
        assertThat(histories()).isEmpty();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("주문·결제 트랜잭션 밖에서 부르면 포인트만 따로 커밋되지 않도록 실행 전에 거부한다")
    void requiresExistingTransaction() {
        // when
        Throwable thrown = catchThrowable(() -> pointCommandService.confirmEarning(ORDER_ID));

        // then
        assertThat(thrown).isInstanceOf(IllegalTransactionStateException.class);
    }

    // 주문 생성 흐름: 주문 INSERT 뒤 포인트 예약 → 재고 차감 뒤 적립 예정 생성
    private void placeOrder(long productAmount, long usedPoint) {
        pointCommandService.reserveUsage(MEMBER_ID, ORDER_ID, usedPoint);
        pointCommandService.createPendingEarning(MEMBER_ID, ORDER_ID, productAmount, usedPoint, RATE);
        flushAndClear();
    }

    // 만료일이 expiresOn이 되도록 1년 전에 적립을 확정한 적립 건
    private PointEarning lot(long seq, long amount, LocalDate expiresOn) {
        PointEarning earning = PointEarning.pending(MEMBER_ID, LOT_ORDER_BASE + seq, amount);
        earning.complete(expiresOn.minusYears(1).atTime(10, 0));
        return pointEarningRepository.save(earning);
    }

    // 적립 건 잔량 합을 회원 잔액으로 맞춘다
    private void holdPoints(PointEarning... lots) {
        long total = Arrays.stream(lots).mapToLong(PointEarning::getRemainingAmount).sum();
        memberPointRepository.save(MemberPoint.builder().memberId(MEMBER_ID).balance(total).build());
        flushAndClear();
    }

    // 만료일이 지나기 전에 이 주문이 결제까지 마친 상태. 잔액은 쓰고 남은 잔량과 같다
    private void usedByOrder(PointEarning lot, long amount) {
        lot.use(amount);
        PointUsage usage = PointUsage.reserve(lot, ORDER_ID, amount, NOW.minusDays(2));
        usage.confirm(NOW.minusDays(2));
        pointUsageRepository.save(usage);
        pointEarningRepository.save(PointEarning.pending(MEMBER_ID, ORDER_ID, 0L));
        memberPointRepository.save(MemberPoint.builder().memberId(MEMBER_ID).balance(lot.getRemainingAmount()).build());
        flushAndClear();
    }

    private PointEarning earning(PointEarning lot) {
        return pointEarningRepository.findById(lot.getId()).orElseThrow();
    }

    private PointEarning orderEarning() {
        return pointEarningRepository.findByOrderId(ORDER_ID).orElseThrow();
    }

    private List<PointUsage> usages() {
        return pointUsageRepository.findAllByOrderIdOrderByIdAsc(ORDER_ID);
    }

    private long balance() {
        return memberPointRepository.findByMemberIdForUpdate(MEMBER_ID).orElseThrow().getBalance();
    }

    private List<PointHistory> histories() {
        return pointHistoryRepository.findAll().stream()
            .filter(history -> MEMBER_ID.equals(history.getMemberId()))
            .sorted(Comparator.comparing(PointHistory::getId))
            .toList();
    }

    private List<PointHistory> histories(PointHistoryType type) {
        return histories().stream().filter(history -> history.getType() == type).toList();
    }

    private void flushAndClear() {
        em.flush();
        em.clear();
    }
}
