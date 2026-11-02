package com.recipick.infra.portone;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipick.infra.portone.dto.PortOneCancellation;
import com.recipick.infra.portone.dto.PortOneCancellationStatus;
import com.recipick.infra.portone.dto.PortOneCancellationTrigger;
import com.recipick.infra.portone.dto.PortOnePayment;
import com.recipick.infra.portone.dto.PortOnePaymentAmount;
import com.recipick.infra.portone.dto.PortOnePaymentResponse;
import com.recipick.infra.portone.dto.PortOnePaymentStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 멤버십 환불 정합화가 쓰는 조회 필드(storeId·amount.cancelled·cancellations[].reason)의 역직렬화와 호환 생성자.
// 상품 결제 경로의 기존 생성자는 새 필드를 null로 두고, null은 0이나 빈 값으로 바뀌지 않는다
class PortOnePaymentRefundFieldsTest {

    private final ObjectMapper mapper = PortOneClientConfig.newPortOneObjectMapper();

    @Test
    @DisplayName("GET 응답의 storeId·amount.cancelled·취소 reason이 도메인 DTO까지 보존된다")
    void refundFieldsAreMapped() throws Exception {
        // given
        String json = """
            {"id":"mpay_1","transactionId":"tx_1","storeId":"store-1","status":"PARTIAL_CANCELLED",
             "amount":{"total":3900,"paid":3900,"cancelled":1862},"currency":"KRW","paidAt":"2027-02-28T01:03:00Z",
             "cancellations":[{"id":"cxl_1","status":"SUCCEEDED","totalAmount":1862,
               "reason":"RECIPICK_MEMBERSHIP_REFUND_V1_10_mrf_v1_x","requestedAt":"2027-03-10T06:00:00Z",
               "cancelledAt":"2027-03-10T06:00:03Z","trigger":"API"}]}
            """;

        // when
        PortOnePayment payment = PortOnePayment.from(mapper.readValue(json, PortOnePaymentResponse.class));

        // then
        assertThat(payment.storeId()).isEqualTo("store-1");
        assertThat(payment.cancelledAmount()).isEqualTo(1_862L);
        assertThat(payment.status()).isEqualTo(PortOnePaymentStatus.PARTIAL_CANCELED);
        assertThat(payment.cancellations()).hasSize(1);
        PortOneCancellation cancellation = payment.cancellations().get(0);
        assertThat(cancellation.reason()).isEqualTo("RECIPICK_MEMBERSHIP_REFUND_V1_10_mrf_v1_x");
        assertThat(cancellation.trigger()).isEqualTo(PortOneCancellationTrigger.API);
        assertThat(cancellation.canceledAt()).isEqualTo(LocalDateTime.of(2027, 3, 10, 15, 0, 3));
    }

    @Test
    @DisplayName("상점·누적 취소액·reason이 없는 응답은 null로 남고(0 아님), 기존 생성자는 새 필드를 null로 위임한다")
    void missingFieldsStayNullAndCompatConstructorsDelegate() throws Exception {
        // given
        String json = """
            {"id":"mpay_1","status":"PAID","amount":{"total":3900,"paid":3900},"currency":"KRW",
             "paidAt":"2027-02-28T01:03:00Z"}
            """;

        // when
        PortOnePayment parsed = PortOnePayment.from(mapper.readValue(json, PortOnePaymentResponse.class));
        PortOnePayment legacy = new PortOnePayment("mpay_1", "tx", PortOnePaymentStatus.PAID, 3_900L, "KRW",
            LocalDateTime.of(2027, 2, 28, 10, 3), null, null, List.of());
        PortOnePaymentAmount amount = new PortOnePaymentAmount(3_900L, 3_900L);
        PortOnePaymentResponse.Cancellation wire = new PortOnePaymentResponse.Cancellation("cxl_1",
            PortOneCancellationStatus.SUCCEEDED, 1_862L, null, null, PortOneCancellationTrigger.API);

        // then
        assertThat(parsed.storeId()).isNull();
        assertThat(parsed.cancelledAmount()).isNull();
        assertThat(parsed.cancellations()).isEmpty();
        assertThat(legacy.storeId()).isNull();
        assertThat(legacy.cancelledAmount()).isNull();
        assertThat(amount.cancelled()).isNull();
        assertThat(wire.reason()).isNull();
        assertThat(PortOneCancellation.from(wire).reason()).isNull();
    }
}
