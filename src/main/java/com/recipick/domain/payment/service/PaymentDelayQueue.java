package com.recipick.domain.payment.service;

import com.recipick.infra.redis.RedisDelayQueue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

// 결제 도메인의 지연 큐. 큐 이름과 작업 형식을 한 곳에 모아 Facade·Worker가 문자열을 직접 다루지 않게 한다
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentDelayQueue {

    public static final String QUEUE_KEY = "payment:delay-queue";

    private final RedisDelayQueue redisDelayQueue;

    // 신규 등록. 같은 작업이 이미 있으면 더 이른 시각만 남긴다 ㅡ 재시도 대기 중인 작업을 앞당기지 않는다
    public void enqueueIfEarlier(PaymentDelayTask task, Instant executeAt) {
        redisDelayQueue.enqueueIfEarlier(QUEUE_KEY, task.member(), executeAt);
    }

    // Worker가 backoff 뒤 다시 하기로 정한 경우. 시각을 뒤로 미룬다
    public void reschedule(PaymentDelayTask task, Instant executeAt) {
        redisDelayQueue.reschedule(QUEUE_KEY, task.member(), executeAt);
    }

    // 형식이 깨진 member는 WARN 후 버린다. 큐에서는 이미 빠졌지만 원본은 DB에 있어 Fallback이 다시 등록한다
    public List<PaymentDelayTask> popDue(Instant now, int batchSize) {
        return redisDelayQueue.popDue(QUEUE_KEY, now, batchSize).stream()
            .map(member -> {
                Optional<PaymentDelayTask> task = PaymentDelayTask.parse(member);
                if (task.isEmpty()) {
                    log.warn("지연 큐에 형식이 깨진 작업이 있어 버립니다: member{}", member);
                }
                return task;
            })
            .flatMap(Optional::stream)
            .toList();
    }
}
