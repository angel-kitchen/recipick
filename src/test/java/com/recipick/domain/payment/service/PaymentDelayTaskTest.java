package com.recipick.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class PaymentDelayTaskTest {

    @Test
    @DisplayName("member는 종류와 ID를 콜론으로 잇는다")
    void member() {
        // when
        String webhook = PaymentDelayTask.webhookProcess(100L).member();
        String refund = PaymentDelayTask.refundReconcile(55L).member();
        String systemRefund = PaymentDelayTask.systemRefundExecute(77L).member();
        String paymentExpire = PaymentDelayTask.paymentExpire(77L).member();

        // then
        assertThat(webhook).isEqualTo("WEBHOOK_PROCESS:100");
        assertThat(refund).isEqualTo("REFUND_RECONCILE:55");
        assertThat(systemRefund).isEqualTo("SYSTEM_REFUND_EXECUTE:77");
        assertThat(paymentExpire).isEqualTo("PAYMENT_EXPIRE:77");
    }

    @Test
    @DisplayName("member를 다시 읽으면 같은 작업이다")
    void parseRoundTrip() {
        // given
        PaymentDelayTask refund = PaymentDelayTask.refundReconcile(55L);
        PaymentDelayTask systemRefund = PaymentDelayTask.systemRefundExecute(77L);
        PaymentDelayTask paymentExpire = PaymentDelayTask.paymentExpire(77L);

        // when
        Optional<PaymentDelayTask> parsedRefund = PaymentDelayTask.parse(refund.member());
        Optional<PaymentDelayTask> parsedSystemRefund = PaymentDelayTask.parse(systemRefund.member());
        Optional<PaymentDelayTask> parsedPaymentExpire = PaymentDelayTask.parse(paymentExpire.member());

        // then
        assertThat(parsedRefund).contains(refund);
        assertThat(parsedSystemRefund).contains(systemRefund);
        assertThat(parsedPaymentExpire).contains(paymentExpire);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"WEBHOOK_PROCESS", "WEBHOOK_PROCESS:", "WEBHOOK_PROCESS:abc", "UNKNOWN_TYPE:1", ":1",
        "WEBHOOK_PROCESS:0", "WEBHOOK_PROCESS:-1"})
    @DisplayName("형식이 깨진 member는 빈 값이다 — 예외로 Worker를 멈추지 않는다")
    void parseMalformed(String member) {
        // when
        Optional<PaymentDelayTask> parsed = PaymentDelayTask.parse(member);

        // then
        assertThat(parsed).isEmpty();
    }

    @Test
    @DisplayName("종류나 ID가 없거나 ID가 1 미만인 작업은 만들 수 없다 — ID는 DB PK다")
    void requireTypeAndPositiveId() {
        // when, then
        assertThatThrownBy(() -> new PaymentDelayTask(null, 1L)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PaymentDelayTask(PaymentDelayTaskType.WEBHOOK_PROCESS, null))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PaymentDelayTask(PaymentDelayTaskType.WEBHOOK_PROCESS, 0L))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
