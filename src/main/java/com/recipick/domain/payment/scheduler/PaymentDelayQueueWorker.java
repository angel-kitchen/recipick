package com.recipick.domain.payment.scheduler;

import com.recipick.domain.payment.facade.PaymentReconciliationFacade;
import com.recipick.domain.payment.facade.RefundReconciliationFacade;
import com.recipick.domain.payment.service.PaymentDelayQueue;
import com.recipick.domain.payment.service.PaymentDelayTask;
import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// 결제 지연 큐 소비자. 실행 시각이 된 작업을 Redis에서 꺼내 종류별 Facade에 넘기기만 한다 — 업무 판단은 여기 없다.
// 한 작업의 예외가 같은 배치의 다른 작업을 막지 않게 작업 단위로 잡는다. 큐에서는 이미 빠졌지만 원본은 DB에 있어
// Fallback Sweep이 다시 등록한다
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentDelayQueueWorker {

    // 한 번에 꺼내는 작업 수. 배치가 길어져도 다음 poll이 fixedDelay 뒤에 이어지므로 크게 잡지 않는다
    static final int BATCH_SIZE = 20;

    private final PaymentDelayQueue paymentDelayQueue;
    private final PaymentReconciliationFacade paymentReconciliationFacade;
    private final RefundReconciliationFacade refundReconciliationFacade;
    private final Clock clock;

    // 실행 시각이 된 작업을 꺼내 처리한다. 주기 실행은 PaymentScheduledTasks가 맡고, 테스트는 이 메서드를 직접 부른다.
    // 여러 인스턴스가 함께 돌아도 popDue가 원자적이라 같은 작업을 둘이 꺼내지 않는다
    public void poll() {
        List<PaymentDelayTask> due = paymentDelayQueue.popDue(clock.instant(), BATCH_SIZE);
        for (PaymentDelayTask task : due) {
            run(task);
        }
    }

    private void run(PaymentDelayTask task) {
        try {
            switch (task.type()) {
                case WEBHOOK_PROCESS -> paymentReconciliationFacade.processWebhook(task.id());
                case REFUND_RECONCILE -> refundReconciliationFacade.reconcile(task.id());
            }
        } catch (RuntimeException e) {
            log.error("지연 큐 작업 처리 중 예외: task={}", task.member(), e);
        }
    }
}
