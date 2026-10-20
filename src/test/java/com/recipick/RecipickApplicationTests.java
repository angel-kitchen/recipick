package com.recipick;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RecipickApplicationTests extends IntegrationTestSupport {

    @Test
    @DisplayName("애플리케이션 컨텍스트가 정상적으로 로드된다")
    void contextLoads() {
        // given / when: 공통 테스트 설정으로 애플리케이션을 시작한다.
        // then: 컨텍스트 초기화가 예외 없이 완료된다.
    }
}
