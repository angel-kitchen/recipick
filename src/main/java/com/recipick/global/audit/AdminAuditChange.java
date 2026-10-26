package com.recipick.global.audit;

import com.recipick.global.exception.BusinessException;
import com.recipick.global.exception.CommonErrorCode;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record AdminAuditChange(Object before, Object after) {

    public AdminAuditChange {
        if (!isSafeValue(before) || !isSafeValue(after)) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
    }

    private static boolean isSafeValue(Object value) {
        return value == null || value instanceof Boolean || value instanceof Enum<?>
                || value instanceof LocalDate || value instanceof LocalDateTime
                || value instanceof Byte || value instanceof Short || value instanceof Integer
                || value instanceof Long || value instanceof BigInteger || value instanceof BigDecimal;
    }

    @Override
    public String toString() {
        return "AdminAuditChange[REDACTED]";
    }
}
