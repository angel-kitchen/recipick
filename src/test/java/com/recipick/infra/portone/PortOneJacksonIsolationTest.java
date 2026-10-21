package com.recipick.infra.portone;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipick.IntegrationTestSupport;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.context.ApplicationContext;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestClient;

/**
 * PortOne 전용 ObjectMapper가 전역 Jackson 설정과 MVC 메시지 컨버터에 영향을 주지 않는지 확인한다.
 * global JacksonConfig의 직렬화 규칙 회귀 테스트를 겸한다.
 */
class PortOneJacksonIsolationTest extends IntegrationTestSupport {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private ObjectMapper primaryObjectMapper;

    @Autowired
    private HttpMessageConverters httpMessageConverters;

    @Autowired
    @Qualifier(PortOneClientConfig.REST_CLIENT_BEAN)
    private RestClient portOneRestClient;

    @Test
    @DisplayName("PortOne 전용 ObjectMapper는 빈으로 등록되지 않고, 등록된 ObjectMapper 빈 어디에도 관대한 enum 옵션이 없다")
    void portOneObjectMapperIsNotABean() {
        // given
        String[] names = context.getBeanNamesForType(ObjectMapper.class);

        // then
        assertThat(names).as("ObjectMapper 빈 %d개: %s", names.length, List.of(names))
            .doesNotContain("portOneObjectMapper")
            .isNotEmpty();
        for (String name : names) {
            ObjectMapper mapper = context.getBean(name, ObjectMapper.class);
            assertThat(mapper.isEnabled(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE))
                .as("ObjectMapper 빈 '%s'에 PortOne 전용 옵션이 켜져 있다", name)
                .isFalse();
        }
    }

    @Test
    @DisplayName("Boot의 ObjectMapper 자동구성이 살아 있다")
    void bootAutoConfigurationStillActive() {
        // then
        assertThat(context.containsBean("jacksonObjectMapper")).isTrue();
        assertThat(context.getBeanNamesForType(Jackson2ObjectMapperBuilder.class)).isNotEmpty();
        assertThat(portOneRestClient).isNotNull();
    }

    @Test
    @DisplayName("전역 ObjectMapper는 JacksonConfig대로 시간을 +09:00 문자열로 쓴다")
    void globalMapperKeepsKstTimeFormat() throws Exception {
        // given
        LocalDateTime time = LocalDateTime.of(2026, 10, 14, 20, 30);

        // when
        String json = primaryObjectMapper.writeValueAsString(time);

        // then
        assertThat(json).isEqualTo("\"2026-10-14T20:30:00+09:00\"");
    }

    @Test
    @DisplayName("전역 ObjectMapper는 offset이 붙은 시간을 한국 시간으로 읽는다")
    void globalMapperReadsOffsetAsKst() throws Exception {
        // when
        LocalDateTime parsed = primaryObjectMapper.readValue("\"2026-10-14T11:30:00Z\"", LocalDateTime.class);

        // then
        assertThat(parsed).isEqualTo(LocalDateTime.of(2026, 10, 14, 20, 30));
    }

    @Test
    @DisplayName("전역 ObjectMapper는 JacksonConfig대로 BigDecimal을 소수 문자열로 쓴다")
    void globalMapperKeepsPlainBigDecimal() throws Exception {
        // when
        String json = primaryObjectMapper.writeValueAsString(new BigDecimal("1E+2"));

        // then
        assertThat(json).isEqualTo("\"100\"");
    }

    /**
     * Spring MVC 기본 목록에는 Boot 커스터마이저를 거치지 않은 Jackson 컨버터가 하나 더 들어 있고 Boot가
     * 등록한 빈이 그 앞에 놓인다(Boot 기본 동작). 그래서 "모두 primary"가 아니라 "첫 번째가 primary이고,
     * 어느 것도 PortOne 전용 옵션을 갖지 않는다"를 검증한다.
     */
    @Test
    @DisplayName("MVC가 먼저 쓰는 Jackson 컨버터는 전역 ObjectMapper이고, PortOne 전용 매퍼는 어디에도 없다")
    void mvcConvertersUsePrimaryObjectMapper() {
        // given
        List<MappingJackson2HttpMessageConverter> jacksonConverters = httpMessageConverters.getConverters().stream()
            .filter(converter -> converter.getClass() == MappingJackson2HttpMessageConverter.class)
            .map(MappingJackson2HttpMessageConverter.class::cast)
            .toList();

        // then
        assertThat(jacksonConverters).isNotEmpty();
        assertThat(jacksonConverters.get(0).getObjectMapper()).isSameAs(primaryObjectMapper);
        for (MappingJackson2HttpMessageConverter converter : jacksonConverters) {
            assertThat(converter.getObjectMapper()
                .isEnabled(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE))
                .as("PortOne 전용 옵션이 켜진 매퍼가 MVC 컨버터에 들어 있다")
                .isFalse();
        }
    }
}
