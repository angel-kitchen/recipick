package com.recipick.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.recipick.domain.payment.dto.PaymentCreated;
import com.recipick.domain.payment.entity.Payment;
import com.recipick.domain.payment.entity.enums.PaymentMethod;
import com.recipick.domain.payment.entity.enums.PaymentStatus;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PaymentCommandServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 21, 10, 0, 0);

    @Mock
    private PaymentRepository paymentRepository;

    @Captor
    private ArgumentCaptor<Payment> paymentCaptor;

    private PaymentCommandService paymentCommandService;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL);
        paymentCommandService = new PaymentCommandService(paymentRepository, fixedClock);
        // JPA가 채울 id를 흉내 낸다
        given(paymentRepository.save(any(Payment.class))).willAnswer(invocation -> {
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
    @DisplayName("결제를 두 번 만들면 PortOne 결제 번호가 서로 다르다")
    void createGeneratesDistinctPortonePaymentIds() {
        // when
        PaymentCreated first = paymentCommandService.create(1L, 23800L);
        PaymentCreated second = paymentCommandService.create(2L, 15000L);

        // then
        assertThat(first.portonePaymentId()).isNotEqualTo(second.portonePaymentId());
    }
}
