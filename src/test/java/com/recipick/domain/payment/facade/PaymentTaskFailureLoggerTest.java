package com.recipick.domain.payment.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.Logger;

class PaymentTaskFailureLoggerTest {
    @Test
    @DisplayName("예상하지 못한 오류는 스택을 남기되 예외와 원인 메시지의 비밀값은 제거한다")
    void preservesStackWithoutSecretMessages() {
        // given
        Logger logger = mock(Logger.class);
        RuntimeException error = new IllegalStateException("billing-key-secret",
            new RuntimeException("authorization-secret"));

        // when
        PaymentTaskFailureLogger.log(logger, error, "실패: id={}", 17L);

        // then
        ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
        verify(logger).error(eq("실패: id={}"), arguments.capture());
        Throwable recorded = (Throwable) arguments.getValue()[1];
        assertThat(recorded.toString()).doesNotContain("billing-key-secret");
        assertThat(recorded.getCause().toString()).doesNotContain("authorization-secret");
        assertThat(recorded.getStackTrace()).containsExactly(error.getStackTrace());
    }
}
