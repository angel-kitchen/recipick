package com.recipick.domain.member.dto.request;

import com.recipick.global.audit.AdminAuditAction;
import com.recipick.global.audit.AdminAuditTarget;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import java.time.LocalDateTime;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;

public record AdminAuditSearchRequest(
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
        @Positive Long adminId,
        AdminAuditAction action,
        AdminAuditTarget targetType,
        @Min(0) Integer page,
        @Min(1) @Max(100) Integer size
) {
    public AdminAuditSearchRequest {
        page = page == null ? 0 : page;
        size = size == null ? 20 : size;
    }

    @AssertTrue(message = "종료 시각은 시작 시각보다 뒤여야 합니다.")
    public boolean isPeriodValid() {
        return from == null || to == null || from.isBefore(to);
    }

    public Pageable toPageable() {
        return PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
    }
}
