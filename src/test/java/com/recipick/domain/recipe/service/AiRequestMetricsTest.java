package com.recipick.domain.recipe.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.recipick.domain.recipe.entity.AiRequestType;
import com.recipick.domain.recipe.entity.AiResultStatus;
import com.recipick.infra.ai.dto.AiFailureType;
import com.recipick.infra.ai.dto.AiResult;
import com.recipick.infra.ai.dto.AiUsage;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AiRequestMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final AiRequestMetrics metrics = new AiRequestMetrics(registry);

    @Test
    @DisplayName("성공한 요청은 결과 상태별 건수·응답 시간·토큰 종류별 합계를 남긴다")
    void record_success() {
        // given
        AiResult<List<String>> result =
            AiResult.success(List.of("김치찌개"), new AiUsage("claude-opus-5-5", 900, 120, 800, 1500));

        // when
        metrics.record(AiRequestType.AUTO_CART, AiResultStatus.PREVIEWED, result);
        metrics.record(AiRequestType.AUTO_CART, AiResultStatus.PREVIEWED, result);

        // then
        assertThat(registry.get(AiRequestMetrics.REQUESTS)
            .tags("type", "AUTO_CART", "status", "PREVIEWED", "failure", "NONE")
            .counter().count()).isEqualTo(2);
        assertThat(registry.get(AiRequestMetrics.LATENCY).tags("type", "AUTO_CART")
            .timer().totalTime(TimeUnit.MILLISECONDS)).isEqualTo(3000);
        assertThat(registry.get(AiRequestMetrics.TOKENS).tags("kind", "input", "model", "claude-opus-5-5")
            .counter().count()).isEqualTo(1800);
        assertThat(registry.get(AiRequestMetrics.TOKENS).tags("kind", "cache_read")
            .counter().count()).isEqualTo(1600);
    }

    @Test
    @DisplayName("실패한 요청은 실패 사유를 태그로 남기고, 받지 못한 토큰은 세지 않는다")
    void record_failure() {
        // given
        AiResult<Object> result = AiResult.failure(AiFailureType.TIMEOUT, AiUsage.failed("claude-opus-5-5", 15000));

        // when
        metrics.record(AiRequestType.RECOMMENDATION, AiResultStatus.FALLBACK, result);

        // then
        assertThat(registry.get(AiRequestMetrics.REQUESTS)
            .tags("type", "RECOMMENDATION", "status", "FALLBACK", "failure", "TIMEOUT")
            .counter().count()).isEqualTo(1);
        assertThat(registry.get(AiRequestMetrics.LATENCY).tags("type", "RECOMMENDATION")
            .timer().totalTime(TimeUnit.MILLISECONDS)).isEqualTo(15000);
        assertThat(registry.find(AiRequestMetrics.TOKENS).counters()).isEmpty();
    }

    @Test
    @DisplayName("Prometheus에서는 대시보드가 조회하는 이름(_total, _bucket)으로 나간다")
    void record_exposedWithPrometheusNames() {
        // given
        PrometheusMeterRegistry prometheus = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        AiResult<List<String>> result =
            AiResult.success(List.of(), new AiUsage("claude-opus-5-5", 900, 120, 0, 1500));

        // when
        new AiRequestMetrics(prometheus).record(AiRequestType.AUTO_CART, AiResultStatus.NO_MATCH, result);
        String scrape = prometheus.scrape();

        // then
        assertThat(scrape)
            .contains("recipick_ai_requests_total{failure=\"NONE\",status=\"NO_MATCH\",type=\"AUTO_CART\"} 1.0")
            .contains("recipick_ai_latency_seconds_bucket")
            .contains("recipick_ai_tokens_total{kind=\"input\",model=\"claude-opus-5-5\",type=\"AUTO_CART\"} 900.0");
    }

    @Test
    @DisplayName("요청이 오기 전에 결과 상태·실패 사유 조합과 응답 시간을 0으로 등록해 첫 요청도 증가량에 잡히게 한다")
    void constructor_registersSeriesBeforeFirstRequest() {
        // given
        SimpleMeterRegistry fresh = new SimpleMeterRegistry();

        // when
        new AiRequestMetrics(fresh);

        // then
        assertThat(fresh.get(AiRequestMetrics.REQUESTS)
            .tags("type", "AUTO_CART", "status", "FALLBACK", "failure", "TIMEOUT")
            .counter().count()).isZero();
        assertThat(fresh.get(AiRequestMetrics.REQUESTS)
            .tags("type", "RECOMMENDATION", "status", "RECOMMENDED", "failure", "NONE")
            .counter().count()).isZero();
        assertThat(fresh.get(AiRequestMetrics.LATENCY).tags("type", "RECOMMENDATION").timer().count()).isZero();
        assertThat(fresh.find(AiRequestMetrics.REQUESTS).tags("status", "PREVIEWED", "failure", "TIMEOUT")
            .counter()).isNull();
    }
}
