package com.recipick.domain.payment.service;

import com.recipick.domain.payment.dto.PaymentCreated;
import com.recipick.domain.payment.entity.Payment;
import com.recipick.domain.payment.entity.enums.PaymentMethod;
import com.recipick.domain.payment.entity.enums.PaymentStatus;
import com.recipick.domain.payment.event.PaymentCreatedEvent;
import com.recipick.domain.payment.repository.PaymentRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentCommandService {

    // 주문·결제 생성 시점부터 5분 안에 결제를 끝내야 한다
    private static final Duration PAYMENT_WINDOW = Duration.ofMinutes(5);
    private static final String PORTONE_PAYMENT_ID_PREFIX = "pay_";

    private final PaymentRepository paymentRepository;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;

    // 결제 생성 API는 없다. 주문 생성 Facade가 같은 트랜잭션 안에서 부른다 — 따로 커밋되면 주문 없는 결제가 남고
    // pending_member_id UNIQUE 때문에 그 회원이 다음 주문을 못 만들므로, 바깥 트랜잭션 없이는 호출할 수 없게 한다
    @Transactional(propagation = Propagation.MANDATORY)
    public PaymentCreated create(Long orderId, long payAmount) {
        LocalDateTime now = LocalDateTime.now(clock);
        Payment payment = paymentRepository.save(Payment.builder()
            .orderId(orderId)
            .paymentMethod(PaymentMethod.CARD)
            .portonePaymentId(PORTONE_PAYMENT_ID_PREFIX + UUID.randomUUID())
            .amount(payAmount)
            .status(PaymentStatus.IN_PROGRESS)
            .expiresAt(now.plus(PAYMENT_WINDOW))
            .build());
        // 저장으로 받은 ID가 만료 작업의 member가 되므로 저장 뒤에 발행한다. 실제 예약은 주문 생성 트랜잭션이 커밋된 뒤
        // 리스너가 한다
        eventPublisher.publishEvent(new PaymentCreatedEvent(payment.getId(), payment.getExpiresAt()));
        return PaymentCreated.from(payment);
    }
}
