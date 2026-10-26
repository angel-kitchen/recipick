package com.recipick.global.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.FilterChain;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestLoggingFilterTest {

    private final RequestLoggingFilter filter = new RequestLoggingFilter();
    private final Logger filterLogger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        appender = new ListAppender<>();
        appender.start();
        filterLogger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        filterLogger.detachAppender(appender);
        MDC.clear();
    }

    @Test
    @DisplayName("응답에 UUID 형식의 X-Request-Id가 담기고 요청마다 다르다")
    void traceIdHeader_isUuidAndUnique() throws Exception {
        // given
        FilterChain chain = (request, response) -> { };
        MockHttpServletResponse first = new MockHttpServletResponse();
        MockHttpServletResponse second = new MockHttpServletResponse();

        // when
        filter.doFilter(new MockHttpServletRequest("GET", "/api/categories"), first, chain);
        filter.doFilter(new MockHttpServletRequest("GET", "/api/categories"), second, chain);

        // then
        String firstId = first.getHeader(RequestLoggingFilter.TRACE_ID_HEADER);
        String secondId = second.getHeader(RequestLoggingFilter.TRACE_ID_HEADER);
        assertThat(UUID.fromString(firstId)).isNotNull();
        assertThat(firstId).isNotEqualTo(secondId);
    }

    @Test
    @DisplayName("요청 처리 중 MDC에 같은 추적 ID가 있고 끝나면 지워진다")
    void mdc_hasTraceIdDuringChain_andIsClearedAfter() throws Exception {
        // given
        String[] traceIdInChain = new String[1];
        FilterChain chain = (request, response) -> traceIdInChain[0] = MDC.get("traceId");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        filter.doFilter(new MockHttpServletRequest("GET", "/api/categories"), response, chain);

        // then
        assertThat(traceIdInChain[0]).isEqualTo(response.getHeader(RequestLoggingFilter.TRACE_ID_HEADER));
        assertThat(MDC.get("traceId")).isNull();
    }

    @Test
    @DisplayName("2xx는 INFO, 4xx는 WARN, 5xx는 ERROR로 기록한다")
    void logLevel_followsStatus() throws Exception {
        // given
        int[] statuses = {200, 404, 500};

        // when
        for (int status : statuses) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain chain = (req, res) -> ((MockHttpServletResponse) res).setStatus(status);
            filter.doFilter(new MockHttpServletRequest("GET", "/api/products"), response, chain);
        }

        // then
        assertThat(appender.list).extracting(ILoggingEvent::getLevel)
            .containsExactly(Level.INFO, Level.WARN, Level.ERROR);
    }

    @Test
    @DisplayName("쿼리 문자열은 로그에 남기지 않는다")
    void queryString_isNotLogged() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/products");
        request.setQueryString("keyword=secret-word");
        FilterChain chain = (req, res) -> { };

        // when
        filter.doFilter(request, new MockHttpServletResponse(), chain);

        // then
        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getFormattedMessage()).contains("GET", "/api/products", "status=200")
            .doesNotContain("secret-word");
        assertThat(event.getKeyValuePairs().toString()).doesNotContain("secret-word");
    }

    @Test
    @DisplayName("처리 중 예외가 나면 500으로 ERROR 기록하고 예외는 그대로 던진다")
    void exception_isLoggedAsErrorAndRethrown() {
        // given
        FilterChain chain = (req, res) -> {
            throw new IllegalStateException("boom");
        };

        // when
        Throwable thrown = catchThrowable(() ->
            filter.doFilter(new MockHttpServletRequest("GET", "/api/products"), new MockHttpServletResponse(), chain));

        // then
        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.ERROR);
        assertThat(event.getFormattedMessage()).contains("status=500");
        assertThat(MDC.get("traceId")).isNull();
    }

    @Test
    @DisplayName("웹훅 경로는 추적 ID도 로그도 남기지 않는다")
    void webhook_isSkipped() throws Exception {
        // given
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> { };

        // when
        filter.doFilter(new MockHttpServletRequest("POST", "/api/webhooks/portone"), response, chain);

        // then
        assertThat(appender.list).isEmpty();
        assertThat(response.getHeader(RequestLoggingFilter.TRACE_ID_HEADER)).isNull();
    }
}
