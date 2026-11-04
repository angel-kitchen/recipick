package com.recipick.domain.payment.integration;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.springframework.transaction.support.TransactionTemplate;

// 다른 트랜잭션이 행 잠금을 쥐고 있는 상황을 만든다. 별도 스레드의 실제 트랜잭션이 잠그므로
// 테스트 대상은 MySQL의 잠금 대기 초과를 진짜로 받고, 그 트랜잭션도 진짜로 롤백된다.
// hold()는 잠금을 실제로 잡은 뒤에만 돌아온다 — 시간으로 잠금 시점을 추측하지 않는다
final class RowLockHolder implements AutoCloseable {

    // 테스트가 release()를 잊어도 잠금이 영원히 남지 않게 하는 상한
    private static final long MAX_HOLD_SECONDS = 60;

    private final TransactionTemplate transactionTemplate;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final CountDownLatch locked = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);
    private Future<?> running;

    RowLockHolder(TransactionTemplate transactionTemplate) {
        this.transactionTemplate = transactionTemplate;
    }

    // lockWork는 트랜잭션 안에서 잠금을 잡는 SQL이다. 잡은 뒤 release()까지 커밋하지 않는다
    void hold(Runnable lockWork) {
        running = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
            lockWork.run();
            locked.countDown();
            awaitRelease();
        }));
        try {
            if (!locked.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("잠금 보유 트랜잭션이 잠금을 잡지 못했습니다.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("잠금 획득 대기 중 인터럽트", e);
        }
    }

    // 잠금 보유 트랜잭션을 커밋해 잠금을 푼다. 다른 스레드에서 불러도 된다
    void release() {
        release.countDown();
    }

    // 커밋까지 끝난 것을 확인한다. 이 뒤의 조회·재확인은 잠금 없이 진행된다
    @Override
    public void close() throws Exception {
        release.countDown();
        try {
            if (running != null) {
                running.get(10, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private void awaitRelease() {
        try {
            if (!release.await(MAX_HOLD_SECONDS, TimeUnit.SECONDS)) {
                throw new AssertionError("잠금 해제 신호가 오지 않았습니다.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("잠금 해제 대기 중 인터럽트", e);
        }
    }
}
