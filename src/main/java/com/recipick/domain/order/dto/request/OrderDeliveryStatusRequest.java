package com.recipick.domain.order.dto.request;

import com.recipick.domain.order.entity.enums.MartStatus;
import jakarta.validation.constraints.NotNull;

// 배송 상태 변경 요청. 배송중·배송완료만 의미가 있고, 그 외 값은 Service가 ORDER-017로 거절한다
public record OrderDeliveryStatusRequest(@NotNull MartStatus status) {
}
