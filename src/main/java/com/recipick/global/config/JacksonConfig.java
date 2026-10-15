package com.recipick.global.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.recipick.global.common.TimeConstants;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 시간은 LocalDateTime(한국 시간)으로 저장하지만 API에는 offset을 붙여 {@code 2026-10-14T20:30:00+09:00} 형태로 주고받는다.
 */
@Configuration
public class JacksonConfig {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer localDateTimeOffsetCustomizer() {
        return builder -> builder
                .serializerByType(LocalDateTime.class, new KstLocalDateTimeSerializer())
                .deserializerByType(LocalDateTime.class, new KstLocalDateTimeDeserializer());
    }

    static class KstLocalDateTimeSerializer extends JsonSerializer<LocalDateTime> {

        @Override
        public void serialize(LocalDateTime value, JsonGenerator gen, SerializerProvider serializers)
                throws IOException {
            gen.writeString(value.truncatedTo(ChronoUnit.SECONDS).atZone(TimeConstants.ZONE_ID).format(FORMATTER));
        }
    }

    static class KstLocalDateTimeDeserializer extends JsonDeserializer<LocalDateTime> {

        @Override
        public LocalDateTime deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            String text = parser.getValueAsString();
            try {
                return OffsetDateTime.parse(text, FORMATTER)
                        .atZoneSameInstant(TimeConstants.ZONE_ID)
                        .toLocalDateTime();
            } catch (DateTimeParseException e) {
                // offset 없이 온 값은 한국 시간으로 본다
                return LocalDateTime.parse(text);
            }
        }
    }
}
