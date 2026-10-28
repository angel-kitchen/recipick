package com.recipick.domain.order.entity.enums;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class MartStatusTest {

    // 구현한 기능의 전이만 허용한다. 배송 단계(PREPARING → SHIPPING → DELIVERED)는 그 기능을 만들 때 추가한다
    private static final Set<String> ALLOWED =
            Set.of("NONE->RECEIVED", "RECEIVED->PREPARING", "RECEIVED->REJECTED");

    @ParameterizedTest
    @EnumSource(MartStatus.class)
    @DisplayName("시작 전에서 접수중, 접수중에서 배송준비중·마트 거절로만 바꿀 수 있고 나머지 전이는 모두 막는다")
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
