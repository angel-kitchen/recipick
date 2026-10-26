package com.recipick.infra.portone;

import static org.assertj.core.api.Assertions.assertThat;

import com.recipick.infra.portone.dto.PortOneCancellation;
import com.recipick.infra.portone.dto.PortOneCancellationStatus;
import com.recipick.infra.portone.dto.PortOneCancellationTrigger;
import com.recipick.infra.portone.dto.PortOnePayment;
import com.recipick.infra.portone.dto.PortOnePaymentAmount;
import com.recipick.infra.portone.dto.PortOnePaymentFailure;
import com.recipick.infra.portone.dto.PortOnePaymentResponse;
import com.recipick.infra.portone.dto.PortOnePaymentStatus;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PortOnePaymentTest {

    @Test
    @DisplayName("UTC paidAt을 한국 시간 LocalDateTime으로 바꾼다")
    void convertPaidAtFromUtc() {
        // given
        PortOnePaymentResponse response =
            paidResponse(OffsetDateTime.parse("2026-10-20T10:03:11Z"));

        // when
        PortOnePayment payment = PortOnePayment.from(response);

        // then
        assertThat(payment.paidAt())
            .isEqualTo(LocalDateTime.of(2026, 10, 20, 19, 3, 11));
    }

    @Test
    @DisplayName("+09:00 paidAt도 같은 한국 시간이 된다")
    void convertPaidAtFromKstOffset() {
        // given
        PortOnePaymentResponse response =
            paidResponse(OffsetDateTime.parse("2026-10-20T19:03:11+09:00"));

        // when
        PortOnePayment payment = PortOnePayment.from(response);

        // then
        assertThat(payment.paidAt())
            .isEqualTo(LocalDateTime.of(2026, 10, 20, 19, 3, 11));
    }

    @Test
    @DisplayName("paidAt·amount·failure가 없으면 null로 둔다")
    void nullableFields() {
        // given
        PortOnePaymentResponse response = new PortOnePaymentResponse(
            "pay_1",
            "tx_1",
            PortOnePaymentStatus.READY,
            null,
            "KRW",
            null,
            null,
            null,
            null
        );

        // when
        PortOnePayment payment = PortOnePayment.from(response);

        // then
        assertThat(payment.paidAt()).isNull();
        assertThat(payment.totalAmount()).isNull();
        assertThat(payment.customDataOrderId()).isNull();
        assertThat(payment.failureSummary()).isNull();
        assertThat(payment.cancellations()).isEmpty();
    }

    @Test
    @DisplayName("총 결제금액·customData 주문 ID·식별자를 옮긴다")
    void copyIdentifiersAndAmount() {
        // given
        PortOnePaymentResponse response =
            paidResponse(OffsetDateTime.parse("2026-10-20T10:03:11Z"));

        // when
        PortOnePayment payment = PortOnePayment.from(response);

        // then
        assertThat(payment.id()).isEqualTo("pay_1");
        assertThat(payment.transactionId()).isEqualTo("tx_1");
        assertThat(payment.status()).isEqualTo(PortOnePaymentStatus.PAID);
        assertThat(payment.totalAmount()).isEqualTo(23800L);
        assertThat(payment.currency()).isEqualTo("KRW");
        assertThat(payment.customDataOrderId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("실패 사유는 reason·pgCode·pgMessage를 한 줄로 합친다")
    void failureSummary() {
        // given
        PortOnePaymentResponse response = new PortOnePaymentResponse(
            "pay_1",
            "tx_1",
            PortOnePaymentStatus.FAILED,
            null,
            null,
            null,
            null,
            new PortOnePaymentFailure("card declined", "E001", "한도 초과"),
            null
        );

        // when
        PortOnePayment payment = PortOnePayment.from(response);

        // then
        assertThat(payment.failureSummary())
            .isEqualTo("card declined | E001 | 한도 초과");
    }

    @Test
    @DisplayName("실패 사유 일부가 비어 있으면 있는 값만 합치고, 전부 비면 null")
    void failureSummaryNullSafe() {
        // given
        PortOnePaymentFailure partial =
            new PortOnePaymentFailure(null, "E001", "  ");
        PortOnePaymentFailure empty =
            new PortOnePaymentFailure(null, null, null);

        // when
        String partialSummary = partial.summary();
        String emptySummary = empty.summary();

        // then
        assertThat(partialSummary).isEqualTo("E001");
        assertThat(emptySummary).isNull();
    }

    @Test
    @DisplayName("실패 사유가 500자를 넘으면 500자에서 자른다")
    void failureSummaryTruncated() {
        // given
        PortOnePaymentFailure longFailure =
            new PortOnePaymentFailure("x".repeat(600), null, null);

        // when
        String summary = longFailure.summary();

        // then
        assertThat(summary).hasSize(500);
    }

    @Test
    @DisplayName("취소 내역을 순서대로 옮기고 시각을 한국 시간으로 바꾼다")
    void copyCancellations() {
        // given
        PortOnePaymentResponse response = new PortOnePaymentResponse(
            "pay_1",
            "tx_1",
            PortOnePaymentStatus.CANCELED,
            new PortOnePaymentAmount(23800L, 23800L),
            "KRW",
            null,
            null,
            null,
            List.of(new PortOnePaymentResponse.Cancellation(
                "cancel_1",
                PortOneCancellationStatus.SUCCEEDED,
                23800L,
                OffsetDateTime.parse("2026-10-25T03:00:00Z"),
                OffsetDateTime.parse("2026-10-25T03:00:10Z"),
                PortOneCancellationTrigger.API))
        );

        // when
        PortOnePayment payment = PortOnePayment.from(response);

        // then
        assertThat(payment.cancellations()).hasSize(1);
        PortOneCancellation cancellation = payment.cancellations().get(0);
        assertThat(cancellation.id()).isEqualTo("cancel_1");
        assertThat(cancellation.canceledAt()).isEqualTo(LocalDateTime.of(2026, 10, 25, 12, 0, 10));
    }

    @Test
    @DisplayName("취소 내역 없이 만드는 결제 확정용 생성자는 빈 목록을 갖는다")
    void emptyCancellationsByDefault() {
        // when
        PortOnePayment payment = new PortOnePayment(
            "pay_1", "tx_1", PortOnePaymentStatus.PAID, 23800L, "KRW", null, null, null);

        // then
        assertThat(payment.cancellations()).isEmpty();
    }

    private static PortOnePaymentResponse paidResponse(OffsetDateTime paidAt) {
        return new PortOnePaymentResponse(
            "pay_1",
            "tx_1",
            PortOnePaymentStatus.PAID,
            new PortOnePaymentAmount(23800L, 23800L),
            "KRW",
            paidAt,
            "{\"orderId\":10}",
            null,
            null
        );
    }
}
