package com.recipick.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.recipick.domain.payment.dto.PaymentCreated;
import com.recipick.domain.payment.entity.Payment;
import com.recipick.domain.payment.entity.enums.PaymentMethod;
import com.recipick.domain.payment.entity.enums.PaymentStatus;
import com.recipick.domain.payment.event.PaymentCreatedEvent;
import com.recipick.domain.payment.repository.PaymentRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PaymentCommandServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 21, 10, 0, 0);

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Captor
    private ArgumentCaptor<Payment> paymentCaptor;

    @Captor
    private ArgumentCaptor<PaymentCreatedEvent> eventCaptor;

    private PaymentCommandService paymentCommandService;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL);
        paymentCommandService = new PaymentCommandService(paymentRepository, fixedClock, eventPublisher);
        // JPA가 채울 id를 흉내 낸다. 0원 카드 결제 거절 테스트는 저장까지 가지 않으므로 lenient로 둔다
        lenient().when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            ReflectionTestUtils.setField(payment, "id", 10L);
            return payment;
        });
    }

    @Test
    @DisplayName("주문 ID와 결제 금액으로 IN_PROGRESS 카드 결제를 만들고, 만료는 멈춘 시계 기준 5분 뒤다")
    void create() {
        // when
        PaymentCreated created = paymentCommandService.create(1L, 23800L);

        // then
        verify(paymentRepository).save(paymentCaptor.capture());
        Payment saved = paymentCaptor.getValue();
        assertThat(saved.getOrderId()).isEqualTo(1L);
        assertThat(saved.getPaymentMethod()).isEqualTo(PaymentMethod.CARD);
        assertThat(saved.getStatus()).isEqualTo(PaymentStatus.IN_PROGRESS);
        assertThat(saved.getAmount()).isEqualTo(23800L);
        assertThat(saved.getExpiresAt()).isEqualTo(NOW.plusMinutes(5));
        assertThat(saved.getPortonePaymentId()).startsWith("pay_");
        assertThatCode(() -> UUID.fromString(saved.getPortonePaymentId().substring(4))).doesNotThrowAnyException();

        assertThat(created.paymentId()).isEqualTo(10L);
        assertThat(created.portonePaymentId()).isEqualTo(saved.getPortonePaymentId());
        assertThat(created.amount()).isEqualTo(23800L);
        assertThat(created.expiresAt()).isEqualTo(NOW.plusMinutes(5));
    }

    @Test
    @DisplayName("결제를 저장한 뒤 저장된 결제 ID와 만료 시각으로 생성 이벤트를 발행한다 — 실제 예약은 커밋 뒤 리스너가 한다")
    void publishCreatedEventAfterSave() {
        // when
        PaymentCreated created = paymentCommandService.create(1L, 23800L);

        // then
        InOrder inOrder = inOrder(paymentRepository, eventPublisher);
        inOrder.verify(paymentRepository).save(any(Payment.class));
        inOrder.verify(eventPublisher).publishEvent(eventCaptor.capture());
        PaymentCreatedEvent event = eventCaptor.getValue();
        assertThat(event.paymentId()).isEqualTo(10L);
        assertThat(event.expiresAt()).isEqualTo(NOW.plusMinutes(5));
        assertThat(created.paymentId()).isEqualTo(event.paymentId());
        assertThat(created.expiresAt()).isEqualTo(event.expiresAt());
    }

    @Test
    @DisplayName("결제를 두 번 만들면 PortOne 결제 번호가 서로 다르다")
    void createGeneratesDistinctPortonePaymentIds() {
        // when
        PaymentCreated first = paymentCommandService.create(1L, 23800L);
        PaymentCreated second = paymentCommandService.create(2L, 15000L);

        // then
        assertThat(first.portonePaymentId()).isNotEqualTo(second.portonePaymentId());
    }

    @Test
    @DisplayName("0원은 PortOne에 보낼 수 없는 금액이라 카드 결제로 만들지 않고 저장·이벤트 발행도 하지 않는다")
    void rejectZeroAmountCardPayment() {
        // when
        Throwable thrown = catchThrowable(() -> paymentCommandService.create(1L, 0L));

        // then
        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(paymentRepository, eventPublisher);
    }

    @Test
    @DisplayName("0원 결제는 멈춘 시계 기준으로 바로 결제 완료 상태로 저장하고 결제 완료 시각을 돌려준다")
    void createPointsOnly() {
        // when
        PaymentCreated created = paymentCommandService.createPointsOnly(1L);

        // then
        verify(paymentRepository).save(paymentCaptor.capture());
        Payment saved = paymentCaptor.getValue();
        assertThat(saved.getOrderId()).isEqualTo(1L);
        assertThat(saved.getPaymentMethod()).isEqualTo(PaymentMethod.POINTS_ONLY);
        assertThat(saved.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(saved.getAmount()).isZero();
        assertThat(saved.getPaidAt()).isEqualTo(NOW);
        assertThat(saved.getPortonePaymentId()).isNull();
        assertThat(saved.getExpiresAt()).isNull();

        assertThat(created.paymentId()).isEqualTo(10L);
        assertThat(created.portonePaymentId()).isNull();
        assertThat(created.amount()).isZero();
        assertThat(created.expiresAt()).isNull();
        assertThat(created.paidAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("0원 결제는 만료될 결제 대기가 없어 생성 이벤트를 발행하지 않는다 — 5분 만료 작업이 예약되지 않는다")
    void createPointsOnlyDoesNotPublishCreatedEvent() {
        // when
        paymentCommandService.createPointsOnly(1L);

        // then
        verify(eventPublisher, never()).publishEvent(any(PaymentCreatedEvent.class));
        verifyNoInteractions(eventPublisher);
    }
}
