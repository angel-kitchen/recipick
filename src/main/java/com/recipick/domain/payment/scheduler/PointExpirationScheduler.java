package com.recipick.domain.payment.scheduler;

import com.recipick.domain.payment.service.PointCommandService;
import com.recipick.domain.payment.service.PointQueryService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 매일 00:00(KST) 만료일이 지난 포인트를 소멸시킨다. 기준 날짜는 실행 시작 때 한 번 정해 배치 전체에 같은 날짜를 쓴다.
// 대상: 확정 적립 건 중 잔량이 남았고 expiresOn < 오늘인 것(만료일 당일까지는 사용 가능).
//
// 실행 스레드: 포인트 전용 스케줄러(PointSchedulingConfig)에서 돈다. 배치가 길어져도 기본 스케줄러의 결제 지연 큐 폴링을 막지 않는다.
//
// 락: 분산 락을 두지 않는다. 회원마다 새 트랜잭션에서 MEMBER_POINT → POINT_EARNING(id 오름차순)을 잠그고 상태를 다시 보므로,
// 서버 여러 대(무중단 배포 중 겹침 포함)가 동시에 돌아도 늦게 온 쪽은 소멸할 대상이 없어 아무것도 바꾸지 않는다.
//
// 실패: 한 회원이 실패해도 그 회원만 롤백하고 다음 회원을 계속 처리한다. 실패한 회원은 다음 날 배치나
// 그 회원의 다음 포인트 사용(지연 만료)에서 다시 처리된다. 대상 회원 조회 자체가 실패하면 이번 실행을 끝낸다
@Slf4j
@Profile("!test")
@Component
@RequiredArgsConstructor
public class PointExpirationScheduler {

    // 한 번에 읽는 대상 회원 수. 회원 ID만 읽으므로 가볍다
    static final int BATCH_SIZE = 100;

    private final PointQueryService pointQueryService;
    private final PointCommandService pointCommandService;
    private final Clock clock;

    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul",
        scheduler = PointSchedulingConfig.POINT_EXPIRATION_TASK_SCHEDULER)
    public void expire() {
        expireAll(LocalDate.now(clock));
    }

    // 회원 ID 순으로 끊어 읽고(마지막으로 본 회원 다음부터) 회원마다 따로 커밋한다.
    // 처리하면서 대상이 줄어들어 OFFSET으로 넘기면 회원을 건너뛸 수 있다
    public void expireAll(LocalDate today) {
        log.info("포인트 만료 배치 시작: today={}", today);
        long afterMemberId = 0L;
        int processed = 0;
        int failed = 0;
        long expiredTotal = 0L;
        while (true) {
            List<Long> memberIds =
                pointQueryService.findExpirationCandidateMemberIds(today, afterMemberId, BATCH_SIZE);
            if (memberIds.isEmpty()) {
                break;
            }
            for (Long memberId : memberIds) {
                try {
                    expiredTotal += pointCommandService.expireMember(memberId, today);
                    processed++;
                } catch (RuntimeException e) {
                    failed++;
                    log.error("포인트 만료 실패, 다음 회원 계속: memberId={}, today={}", memberId, today, e);
                }
            }
            afterMemberId = memberIds.get(memberIds.size() - 1);
        }
        log.info("포인트 만료 배치 완료: today={}, processed={}, failed={}, expiredTotal={}",
            today, processed, failed, expiredTotal);
    }
}
