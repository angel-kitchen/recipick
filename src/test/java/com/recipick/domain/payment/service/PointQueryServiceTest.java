package com.recipick.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.recipick.domain.payment.dto.response.PointBalanceResponse;
import com.recipick.domain.payment.dto.response.PointEarningResponse;
import com.recipick.domain.payment.dto.response.PointHistoryResponse;
import com.recipick.domain.payment.entity.PointEarning;
import com.recipick.domain.payment.entity.PointHistory;
import com.recipick.domain.payment.entity.enums.PointHistoryType;
import com.recipick.domain.payment.repository.PointEarningRepository;
import com.recipick.domain.payment.repository.PointHistoryRepository;
import com.recipick.domain.payment.repository.dto.PointExpirySummary;
import com.recipick.global.response.PageResponse;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class PointQueryServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 28);
    private static final Long MEMBER_ID = 7L;
    private static final Long ORDER_ID = 1001L;
    private static final Sort LATEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    @Mock
    private PointEarningRepository pointEarningRepository;

    @Mock
    private PointHistoryRepository pointHistoryRepository;

    @Captor
    private ArgumentCaptor<Pageable> pageableCaptor;

    private PointQueryService pointQueryService;

    // 오늘 날짜가 계산 기준이라 시계를 멈춰 두고 직접 조립한다
    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(TODAY.atTime(14, 0).atZone(SEOUL).toInstant(), SEOUL);
        pointQueryService = new PointQueryService(pointEarningRepository, pointHistoryRepository, fixedClock);
    }

    @Test
    @DisplayName("잔액은 오늘 기준 사용 가능 잔량 합이고, 가까운 만료는 가장 이른 만료일 한 줄의 날짜와 합계다")
    void getBalance() {
        // given
        given(pointEarningRepository.sumUsableRemaining(MEMBER_ID, TODAY)).willReturn(8_500L);
        given(pointEarningRepository.findUsableExpiries(MEMBER_ID, TODAY, PageRequest.of(0, 1)))
            .willReturn(List.of(expiry(LocalDate.of(2026, 12, 31), 2_000L)));

        // when
        PointBalanceResponse response = pointQueryService.getBalance(MEMBER_ID);

        // then
        assertThat(response).isEqualTo(new PointBalanceResponse(MEMBER_ID, 8_500L, 2_000L, LocalDate.of(2026, 12, 31)));
    }

    @Test
    @DisplayName("포인트를 써 본 적 없는 회원은 404가 아니라 잔액 0·가까운 만료 0·만료일 없음이다")
    void getBalanceWithoutPoint() {
        // given
        given(pointEarningRepository.sumUsableRemaining(MEMBER_ID, TODAY)).willReturn(0L);
        given(pointEarningRepository.findUsableExpiries(MEMBER_ID, TODAY, PageRequest.of(0, 1)))
            .willReturn(List.of());

        // when
        PointBalanceResponse response = pointQueryService.getBalance(MEMBER_ID);

        // then
        assertThat(response).isEqualTo(new PointBalanceResponse(MEMBER_ID, 0L, 0L, null));
    }

    @Test
    @DisplayName("적립 건 응답은 오늘 쓸 수 있는 잔량만 보여준다 — 배치 전 만료 잔량·다 쓴 건·확정 전후 다른 상태는 0이고 사용 불가다")
    void findEarningsShowsOnlyUsableRemaining() {
        // given
        PointEarning future = completed(300L, TODAY.plusMonths(2));
        PointEarning expiresToday = completed(100L, TODAY);
        PointEarning staleNotExpiredYet = completed(500L, TODAY.minusDays(1));
        PointEarning exhausted = completed(200L, TODAY.plusMonths(1));
        exhausted.use(200L);
        PointEarning pending = PointEarning.pending(MEMBER_ID, ORDER_ID, 50L);
        PointEarning canceled = PointEarning.pending(MEMBER_ID, ORDER_ID + 1, 50L);
        canceled.cancel();
        PointEarning expired = completed(400L, TODAY.minusDays(3));
        expired.expire();
        List<PointEarning> earnings =
            List.of(future, expiresToday, staleNotExpiredYet, exhausted, pending, canceled, expired);
        given(pointEarningRepository.findByMemberId(eq(MEMBER_ID), any(Pageable.class)))
            .willReturn(new PageImpl<>(earnings));

        // when
        PageResponse<PointEarningResponse> response = pointQueryService.findEarnings(MEMBER_ID, PageRequest.of(0, 20));

        // then
        assertThat(response.content())
            .extracting(PointEarningResponse::availableRemainingAmount, PointEarningResponse::usable)
            .containsExactly(
                tuple(300L, true),
                tuple(100L, true),
                tuple(0L, false),
                tuple(0L, false),
                tuple(0L, false),
                tuple(0L, false),
                tuple(0L, false));
        // DB에 남아 있는 잔량(500)은 응답 계산에만 빠지고 적립 건 자체는 바뀌지 않는다
        assertThat(staleNotExpiredYet.getRemainingAmount()).isEqualTo(500L);
    }

    @Test
    @DisplayName("클라이언트가 정렬을 보내도 버리고 최신순(createdAt·id 내림차순)으로 page·size만 유지해 조회한다")
    void findEarningsFixesSort() {
        // given
        given(pointEarningRepository.findByMemberId(eq(MEMBER_ID), any(Pageable.class))).willReturn(Page.empty());

        // when
        pointQueryService.findEarnings(MEMBER_ID, PageRequest.of(2, 30, Sort.by("amount")));

        // then
        verify(pointEarningRepository).findByMemberId(eq(MEMBER_ID), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue()).isEqualTo(PageRequest.of(2, 30, LATEST_FIRST));
    }

    @Test
    @DisplayName("이력은 주문 ID가 없으면 회원 전체를, 있으면 회원과 주문을 함께 조건으로 최신순 조회한다")
    void findHistoryByOrderIdOrNot() {
        // given
        PointEarning earning = PointEarning.pending(MEMBER_ID, ORDER_ID, 50L);
        PointHistory history = PointHistory.earnPending(earning);
        given(pointHistoryRepository.findByMemberId(eq(MEMBER_ID), any(Pageable.class)))
            .willReturn(new PageImpl<>(List.of(history)));
        given(pointHistoryRepository.findByMemberIdAndOrderId(eq(MEMBER_ID), eq(ORDER_ID), any(Pageable.class)))
            .willReturn(Page.empty());

        // when
        PageResponse<PointHistoryResponse> all = pointQueryService.findHistory(MEMBER_ID, null, PageRequest.of(0, 20));
        PageResponse<PointHistoryResponse> byOrder =
            pointQueryService.findHistory(MEMBER_ID, ORDER_ID, PageRequest.of(0, 20));

        // then
        assertThat(all.content()).singleElement().satisfies(row -> {
            assertThat(row.type()).isEqualTo(PointHistoryType.EARN_PENDING);
            assertThat(row.orderId()).isEqualTo(ORDER_ID);
            assertThat(row.amount()).isEqualTo(50L);
        });
        assertThat(byOrder.content()).isEmpty();
        verify(pointHistoryRepository).findByMemberIdAndOrderId(eq(MEMBER_ID), eq(ORDER_ID), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getSort()).isEqualTo(LATEST_FIRST);
    }

    @Test
    @DisplayName("만료 대상 회원 조회는 마지막으로 본 회원 ID와 개수 제한을 그대로 넘긴다")
    void findExpirationCandidateMemberIds() {
        // given
        given(pointEarningRepository.findExpirationCandidateMemberIds(TODAY, 30L, PageRequest.of(0, 100)))
            .willReturn(List.of(40L));

        // when
        List<Long> memberIds = pointQueryService.findExpirationCandidateMemberIds(TODAY, 30L, 100);

        // then
        assertThat(memberIds).containsExactly(40L);
    }

    // 확정 시각을 만료일 1년 전으로 맞춘다
    private PointEarning completed(long amount, LocalDate expiresOn) {
        PointEarning earning = PointEarning.pending(MEMBER_ID, ORDER_ID + amount, amount);
        earning.complete(expiresOn.minusYears(1).atTime(10, 0));
        return earning;
    }

    private PointExpirySummary expiry(LocalDate expiresOn, long amount) {
        return new PointExpirySummary() {
            @Override
            public LocalDate getExpiresOn() {
                return expiresOn;
            }

            @Override
            public Long getAmount() {
                return amount;
            }
        };
    }
}
