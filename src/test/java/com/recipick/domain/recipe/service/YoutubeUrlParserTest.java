package com.recipick.domain.recipe.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.recipick.domain.recipe.exception.RecipeErrorCode;
import com.recipick.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class YoutubeUrlParserTest {

    @ParameterizedTest
    @ValueSource(strings = {
        "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
        "https://youtube.com/watch?v=dQw4w9WgXcQ&t=30s",
        "https://m.youtube.com/watch?feature=share&v=dQw4w9WgXcQ",
        "https://youtu.be/dQw4w9WgXcQ",
        "https://www.youtube.com/shorts/dQw4w9WgXcQ",
        "  https://youtu.be/dQw4w9WgXcQ  "
    })
    @DisplayName("유튜브 주소 형식마다 영상 ID를 뽑는다")
    void extractVideoId(String url) {
        // when
        String videoId = YoutubeUrlParser.extractVideoId(url);

        // then
        assertThat(videoId).isEqualTo("dQw4w9WgXcQ");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("주소가 비어 있으면 영상 없음(null)으로 본다")
    void extractVideoId_empty(String url) {
        // when, then
        assertThat(YoutubeUrlParser.extractVideoId(url)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "https://vimeo.com/123456",
        "https://www.youtube.com/channel/UCabc",
        "https://www.youtube.com/watch?list=PL123",
        "https://youtu.be/short",
        "youtube.com/watch?v=dQw4w9WgXcQ",
        "https://www.youtube.com/watch?v=dQw4w9WgXcQ\\"
    })
    @DisplayName("유튜브 영상 주소가 아니면 INVALID_YOUTUBE_URL")
    void extractVideoId_invalid(String url) {
        // when, then
        assertThatThrownBy(() -> YoutubeUrlParser.extractVideoId(url))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(RecipeErrorCode.INVALID_YOUTUBE_URL);
    }
}
