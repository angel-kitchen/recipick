package com.recipick.infra.portone;

import static org.assertj.core.api.Assertions.assertThat;

import com.recipick.infra.portone.dto.PortOneBillingKeyDeletionObservation;
import com.recipick.infra.portone.dto.PortOneBillingKeyDeletionObservation.Kind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PortOneBillingKeyDeletionObservationTest {
    @Test
    @DisplayName("404는 BILLING_KEY_NOT_FOUND 타입일 때만 삭제 근거이고 401·403은 운영 확인이다")
    void onlyExactNotFoundIsAbsence() {
        // when, then
        assertThat(PortOneBillingKeyDeletionObservation.error(404, "BILLING_KEY_NOT_FOUND").kind())
            .isEqualTo(Kind.ABSENT);
        assertThat(PortOneBillingKeyDeletionObservation.error(404, "OTHER").kind()).isEqualTo(Kind.UNKNOWN);
        assertThat(PortOneBillingKeyDeletionObservation.error(500, "BILLING_KEY_NOT_FOUND").kind())
            .isEqualTo(Kind.UNKNOWN);
        assertThat(PortOneBillingKeyDeletionObservation.error(401, "BILLING_KEY_NOT_FOUND").kind())
            .isEqualTo(Kind.REVIEW_REQUIRED);
    }

    @Test
    @DisplayName("키와 상점이 모두 일치해야 하며 다른 값이면 운영 확인, 빠진 값이면 결과 불명이다")
    void keyAndStoreMustBothMatch() {
        // when, then
        assertThat(found("DELETED", "key", "store").kind()).isEqualTo(Kind.ABSENT);
        assertThat(found("DELETED", "other", "store").kind()).isEqualTo(Kind.REVIEW_REQUIRED);
        assertThat(found("DELETED", "key", "other").kind()).isEqualTo(Kind.REVIEW_REQUIRED);
        assertThat(found("DELETED", "key", null).kind()).isEqualTo(Kind.UNKNOWN);
        assertThat(found("FUTURE_STATUS", "key", "store").kind()).isEqualTo(Kind.UNKNOWN);
    }

    private static PortOneBillingKeyDeletionObservation found(String state, String key, String store) {
        return PortOneBillingKeyDeletionObservation.found("key", "store", state, key, store);
    }
}
