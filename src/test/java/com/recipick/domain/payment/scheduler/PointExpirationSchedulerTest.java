package com.recipick.domain.payment.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.recipick.domain.payment.service.PointCommandService;
import com.recipick.domain.payment.service.PointQueryService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.task.ThreadPoolTaskSchedulerBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@ExtendWith(MockitoExtension.class)
class PointExpirationSchedulerTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 28);
    private static final int BATCH = PointExpirationScheduler.BATCH_SIZE;

    @Mock
    private PointQueryService pointQueryService;

    @Mock
    private PointCommandService pointCommandService;

    private PointExpirationScheduler scheduler;

    // 자정 직후를 흉내 낸다. 시계를 멈춰 기준 날짜를 고정하고 직접 조립한다
    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(TODAY.atTime(0, 0, 1).atZone(SEOUL).toInstant(), SEOUL);
        scheduler = new PointExpirationScheduler(pointQueryService, pointCommandService, fixedClock);
    }

    @Test
    @DisplayName("대상 회원을 회원 ID 순으로 끊어 읽으며 마지막으로 본 회원 다음부터 다시 읽고, 빈 목록이면 끝낸다")
    void expireAllReadsByKeyset() {
        // given
        given(pointQueryService.findExpirationCandidateMemberIds(TODAY, 0L, BATCH)).willReturn(List.of(10L, 20L, 30L));
        given(pointQueryService.findExpirationCandidateMemberIds(TODAY, 30L, BATCH)).willReturn(List.of(40L));
        given(pointQueryService.findExpirationCandidateMemberIds(TODAY, 40L, BATCH)).willReturn(List.of());

        // when
        scheduler.expireAll(TODAY);

        // then
        InOrder inOrder = inOrder(pointQueryService, pointCommandService);
        inOrder.verify(pointQueryService).findExpirationCandidateMemberIds(TODAY, 0L, BATCH);
        inOrder.verify(pointCommandService).expireMember(10L, TODAY);
        inOrder.verify(pointCommandService).expireMember(20L, TODAY);
        inOrder.verify(pointCommandService).expireMember(30L, TODAY);
        inOrder.verify(pointQueryService).findExpirationCandidateMemberIds(TODAY, 30L, BATCH);
        inOrder.verify(pointCommandService).expireMember(40L, TODAY);
        inOrder.verify(pointQueryService).findExpirationCandidateMemberIds(TODAY, 40L, BATCH);
    }

    @Test
    @DisplayName("한 회원의 소멸이 실패해도 예외를 밖으로 던지지 않고 다음 회원을 계속 처리한다")
    void expireAllContinuesAfterMemberFailure() {
        // given
        given(pointQueryService.findExpirationCandidateMemberIds(TODAY, 0L, BATCH)).willReturn(List.of(10L, 20L, 30L));
        given(pointQueryService.findExpirationCandidateMemberIds(TODAY, 30L, BATCH)).willReturn(List.of());
        willThrow(new IllegalStateException("포인트 잔액보다 많이 차감할 수 없습니다."))
            .given(pointCommandService).expireMember(20L, TODAY);

        // when
        scheduler.expireAll(TODAY);

        // then
        verify(pointCommandService).expireMember(10L, TODAY);
        verify(pointCommandService).expireMember(20L, TODAY);
        verify(pointCommandService).expireMember(30L, TODAY);
    }

    @Test
    @DisplayName("대상 회원 조회가 실패하면 이번 실행을 끝내고 아무 회원도 처리하지 않는다")
    void expireAllStopsWhenCandidateQueryFails() {
        // given
        given(pointQueryService.findExpirationCandidateMemberIds(TODAY, 0L, BATCH))
            .willThrow(new IllegalStateException("DB 연결 실패"));

        // when, then
        assertThatThrownBy(() -> scheduler.expireAll(TODAY)).isInstanceOf(IllegalStateException.class);
        verify(pointCommandService, never()).expireMember(anyLong(), any());
    }

    @Test
    @DisplayName("예약 실행은 매일 00:00(KST)에 기본 스케줄러가 아니라 포인트 만료 전용 스케줄러를 지정해 돈다")
    void expireRunsOnPointExpirationScheduler() throws Exception {
        // when
        Scheduled scheduled = PointExpirationScheduler.class.getMethod("expire").getAnnotation(Scheduled.class);

        // then
        assertThat(scheduled.cron()).isEqualTo("0 0 0 * * *");
        assertThat(scheduled.zone()).isEqualTo("Asia/Seoul");
        assertThat(scheduled.scheduler()).isEqualTo("pointExpirationTaskScheduler");
    }

    @Test
    @DisplayName("기본 스케줄러와 포인트 전용 스케줄러는 서로 다른 스레드 풀이고, 포인트 쪽은 스레드 하나에 이름으로 구분된다")
    void schedulingConfigCreatesSeparateSchedulers() {
        // given
        PointSchedulingConfig config = new PointSchedulingConfig();
        ThreadPoolTaskSchedulerBuilder builder = new ThreadPoolTaskSchedulerBuilder().threadNamePrefix("scheduling-");

        // when
        ThreadPoolTaskScheduler defaultScheduler = config.taskScheduler(builder);
        ThreadPoolTaskScheduler pointScheduler = config.pointExpirationTaskScheduler(builder);

        // then
        assertThat(pointScheduler).isNotSameAs(defaultScheduler);
        assertThat(defaultScheduler.getThreadNamePrefix()).isEqualTo("scheduling-");
        assertThat(pointScheduler.getThreadNamePrefix()).isEqualTo("point-expiration-");
        assertThat(pointScheduler.getPoolSize()).isEqualTo(1);
    }

    @Test
    @DisplayName("예약 실행은 시계의 오늘 날짜로 배치를 돌린다")
    void expireUsesToday() {
        // given
        given(pointQueryService.findExpirationCandidateMemberIds(TODAY, 0L, BATCH)).willReturn(List.of());

        // when
        scheduler.expire();

        // then
        verify(pointQueryService).findExpirationCandidateMemberIds(TODAY, 0L, BATCH);
    }
}
