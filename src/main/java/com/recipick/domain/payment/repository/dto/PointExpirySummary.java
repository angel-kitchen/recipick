package com.recipick.domain.payment.repository.dto;

import java.time.LocalDate;

// 만료일별 사용 가능 잔량 합계.
public interface PointExpirySummary {

    LocalDate getExpiresOn();

    Long getAmount();
}
