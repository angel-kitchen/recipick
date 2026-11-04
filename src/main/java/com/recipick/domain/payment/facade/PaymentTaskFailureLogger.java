package com.recipick.domain.payment.facade;

import com.recipick.global.exception.BusinessException;
import java.util.Arrays;
import org.slf4j.Logger;

// PG 예외 메시지·cause에 빌링키가 들어갈 수 있어 예외 타입과 호출 위치만 보존한다.
public final class PaymentTaskFailureLogger {
    private PaymentTaskFailureLogger() {
    }

    public static void log(Logger logger, RuntimeException failure, String message, Object... arguments) {
        if (failure instanceof BusinessException) {
            logger.warn(message, arguments);
            return;
        }
        Object[] withStack = Arrays.copyOf(arguments, arguments.length + 1);
        withStack[arguments.length] = sanitized(failure, 0);
        logger.error(message, withStack);
    }

    private static Throwable sanitized(Throwable failure, int depth) {
        RuntimeException safe = new RuntimeException(failure.getClass().getName());
        safe.setStackTrace(failure.getStackTrace());
        if (failure.getCause() != null && failure.getCause() != failure && depth < 8) {
            safe.initCause(sanitized(failure.getCause(), depth + 1));
        }
        return safe;
    }
}
