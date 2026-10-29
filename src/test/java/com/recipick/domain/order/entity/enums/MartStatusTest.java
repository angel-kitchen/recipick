package com.recipick.domain.order.entity.enums;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class MartStatusTest {

    // 접수 → 배송준비 → 배송중 → 배송완료 순서와 접수 단계의 거절만 허용한다. 같은 상태로의 전이도 막는다
    private static final Set<String> ALLOWED = Set.of("NONE->RECEIVED", "RECEIVED->PREPARING",
        "RECEIVED->REJECTED", "PREPARING->SHIPPING", "SHIPPING->DELIVERED");

    @ParameterizedTest
    @EnumSource(MartStatus.class)
    @DisplayName("시작 전→접수중, 접수중→배송준비중·마트 거절, 배송준비중→배송중, 배송중→배송완료로만 바꿀 수 있고 나머지 전이는 모두 막는다")
    void canTransitionTo(MartStatus from) {
        for (MartStatus to : MartStatus.values()) {
            // when
            boolean allowed = from.canTransitionTo(to);

            // then
            assertThat(allowed)
                .as("%s -> %s", from, to)
                .isEqualTo(ALLOWED.contains(from + "->" + to));
        }
    }
}
