package com.recipick.global.audit;

import com.recipick.global.common.TimeConstants;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.exception.CommonErrorCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record AdminAuditCommand(
        AdminAuditAction action,
        Long targetId,
        Long martId,
        Map<AdminAuditField, AdminAuditChange> changes
) {
    public AdminAuditCommand {
        if (action == null || (targetId == null && !action.targetOptional())
                || (targetId != null && targetId <= 0) || (martId != null && martId <= 0)
                || (action.martRequired() && martId == null)
                || (action.target() == AdminAuditTarget.MART && !Objects.equals(targetId, martId))) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        if (changes == null) {
            changes = Map.of();
        }
        for (var entry : changes.entrySet()) {
            AdminAuditField field = entry.getKey();
            AdminAuditChange change = entry.getValue();
            if (field == null || change == null || !field.supports(action.target())
                    || !field.accepts(change.before()) || !field.accepts(change.after())) {
                throw new BusinessException(CommonErrorCode.INVALID_INPUT);
            }
        }
        changes = Map.copyOf(changes);
    }

    public Map<String, Map<String, Object>> details() {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        changes.forEach((field, change) -> {
            Object before = toStoredValue(change.before());
            Object after = toStoredValue(change.after());
            if (!Objects.equals(before, after)) {
                Map<String, Object> values = new LinkedHashMap<>();
                values.put("before", before);
                values.put("after", after);
                result.put(field.key(), Collections.unmodifiableMap(values));
            }
        });
        return result.isEmpty() ? null : Collections.unmodifiableMap(result);
    }

    private static Object toStoredValue(Object value) {
        if (value instanceof Enum<?> enumeration) {
            return enumeration.name();
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.stripTrailingZeros().toPlainString();
        }
        if (value instanceof LocalDateTime time) {
            return time.atZone(TimeConstants.ZONE_ID).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        }
        if (value instanceof LocalDate date) {
            return date.toString();
        }
        return value;
    }

    @Override
    public String toString() {
        return "AdminAuditCommand[REDACTED]";
    }
}
