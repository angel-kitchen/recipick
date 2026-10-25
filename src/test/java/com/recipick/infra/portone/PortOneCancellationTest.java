package com.recipick.infra.portone;

import static org.assertj.core.api.Assertions.assertThat;

import com.recipick.infra.portone.dto.PortOneCancelOutcome;
import com.recipick.infra.portone.dto.PortOneCancelResult;
import com.recipick.infra.portone.dto.PortOneCancellation;
import com.recipick.infra.portone.dto.PortOneCancellationStatus;
import com.recipick.infra.portone.dto.PortOneCancellationTrigger;
import com.recipick.infra.portone.dto.PortOnePaymentResponse;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PortOneCancellationTest {

    private static final LocalDateTime CANCELED_AT = LocalDateTime.of(2026, 10, 25, 12, 0, 10);

    @Test
    @DisplayName("와이어 취소 내역의 시각을 한국 시간으로 바꾸고 나머지 값을 옮긴다")
    void fromWire() {
        // given
        PortOnePaymentResponse.Cancellation wire = new PortOnePaymentResponse.Cancellation(
            "cancel_1",
            PortOneCancellationStatus.SUCCEEDED,
            23800L,
            OffsetDateTime.parse("2026-10-25T03:00:00Z"),
            OffsetDateTime.parse("2026-10-25T03:00:10Z"),
            PortOneCancellationTrigger.API
        );

        // when
        PortOneCancellation cancellation = PortOneCancellation.from(wire);

        // then
        assertThat(cancellation.id()).isEqualTo("cancel_1");
        assertThat(cancellation.status()).isEqualTo(PortOneCancellationStatus.SUCCEEDED);
        assertThat(cancellation.totalAmount()).isEqualTo(23800L);
        assertThat(cancellation.requestedAt()).isEqualTo(LocalDateTime.of(2026, 10, 25, 12, 0, 0));
        assertThat(cancellation.canceledAt()).isEqualTo(CANCELED_AT);
        assertThat(cancellation.trigger()).isEqualTo(PortOneCancellationTrigger.API);
    }

    @Test
    @DisplayName("상태·경로·시각이 비어 있으면 UNKNOWN·null로 둔다")
    void fromWireWithMissingValues() {
        // given
        PortOnePaymentResponse.Cancellation wire =
            new PortOnePaymentResponse.Cancellation("cancel_1", null, null, null, null, null);

        // when
        PortOneCancellation cancellation = PortOneCancellation.from(wire);

        // then
        assertThat(cancellation.status()).isEqualTo(PortOneCancellationStatus.UNKNOWN);
        assertThat(cancellation.trigger()).isEqualTo(PortOneCancellationTrigger.UNKNOWN);
        assertThat(cancellation.requestedAt()).isNull();
        assertThat(cancellation.canceledAt()).isNull();
    }

    @ParameterizedTest
    @CsvSource({
        "SUCCEEDED, SUCCEEDED",
        "REQUESTED, REQUESTED",
        "FAILED, FAILED",
        "UNKNOWN, UNCONFIRMED"
    })
    @DisplayName("조회한 취소 상태를 취소 API와 같은 결과로 바꾼다 — 모르는 상태는 결과 불명")
    void toCancelResult(PortOneCancellationStatus status, PortOneCancelOutcome expected) {
        // given
        PortOneCancellation cancellation =
            new PortOneCancellation("cancel_1", status, 23800L, null, CANCELED_AT, PortOneCancellationTrigger.API);

        // when
        PortOneCancelResult result = cancellation.toCancelResult();

        // then
        assertThat(result.outcome()).isEqualTo(expected);
        assertThat(result.cancellationId()).isEqualTo("cancel_1");
        assertThat(result.canceledAt()).isEqualTo(CANCELED_AT);
    }

    @Test
    @DisplayName("SUCCEEDED여도 취소 시각이 없으면 시각을 채우지 않는다 — 완료 판단은 정합화 쪽 몫")
    void keepMissingCanceledAt() {
        // given
        PortOneCancellation cancellation = new PortOneCancellation(
            "cancel_1", PortOneCancellationStatus.SUCCEEDED, 23800L, null, null, PortOneCancellationTrigger.API);

        // when
        PortOneCancelResult result = cancellation.toCancelResult();

        // then
        assertThat(result.outcome()).isEqualTo(PortOneCancelOutcome.SUCCEEDED);
        assertThat(result.canceledAt()).isNull();
    }

    @Test
    @DisplayName("취소 ID가 없으면 상태와 관계없이 결과 불명이다 — 어느 환불인지 확정할 수 없다")
    void unconfirmedWithoutId() {
        // given
        PortOneCancellation cancellation = new PortOneCancellation(
            " ", PortOneCancellationStatus.SUCCEEDED, 23800L, null, CANCELED_AT, PortOneCancellationTrigger.API);

        // when
        PortOneCancelResult result = cancellation.toCancelResult();

        // then
        assertThat(result.outcome()).isEqualTo(PortOneCancelOutcome.UNCONFIRMED);
        assertThat(result.cancellationId()).isNull();
    }
}
