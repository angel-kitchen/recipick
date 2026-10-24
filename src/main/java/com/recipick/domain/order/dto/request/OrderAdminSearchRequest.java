package com.recipick.domain.order.dto.request;

import com.recipick.domain.order.entity.enums.MartStatus;

// martId는 SUPER_ADMIN만 고른다. MART_ADMIN은 토큰의 마트로 범위가 고정되고 다른 마트를 지정하면 거절된다
public record OrderAdminSearchRequest(
        MartStatus martStatus,
        Long martId
) {
}
