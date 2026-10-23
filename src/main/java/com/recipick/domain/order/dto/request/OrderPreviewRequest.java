package com.recipick.domain.order.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

// 판정에 쓰는 값만 받는다. 수령인·연락처·주소 문자열은 판정과 관계없어 주문 생성에서만 받는다.
// 법정동 코드 형식 오류는 요구사항상 ORDER-006으로 응답해야 해서 여기서 검증하지 않고 Facade가 검증한다
public record OrderPreviewRequest(
        @NotEmpty @Size(max = 100) List<@NotNull Long> cartItemIds,
        String legalDongCode
) {
}
