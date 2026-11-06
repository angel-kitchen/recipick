package com.recipick.global.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ChoseongTest {

    @Test
    @DisplayName("완성형 한글은 초성으로 바꾸고, 쌍자음·영문·숫자는 그대로 둔다")
    void of_convertsSyllablesToInitials() {
        // when & then
        assertThat(Choseong.of("김치찌개")).isEqualTo("ㄱㅊㅉㄱ");
        assertThat(Choseong.of("까르보나라")).isEqualTo("ㄲㄹㅂㄴㄹ");
        assertThat(Choseong.of("BLT 샌드위치2")).isEqualTo("BLT ㅅㄷㅇㅊ2");
    }

    @Test
    @DisplayName("초성으로만 이뤄진 글자인지 판단한다")
    void isChoseongOnly_detectsInitialsOnly() {
        // when & then
        assertThat(Choseong.isChoseongOnly("ㄱㅊ")).isTrue();
        assertThat(Choseong.isChoseongOnly("김ㅊ")).isFalse();
        assertThat(Choseong.isChoseongOnly("ㅏ")).isFalse();
        assertThat(Choseong.isChoseongOnly("")).isFalse();
    }

    @Test
    @DisplayName("null이면 null을 돌려준다")
    void of_returnsNullForNull() {
        // given
        // when
        // then
        assertThat(Choseong.of(null)).isNull();
    }
}
