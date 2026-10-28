package com.recipick.infra.ai.claude;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "ai.client", havingValue = "claude")
@EnableConfigurationProperties(ClaudeProperties.class)
public class ClaudeClientConfig {

    @Bean(destroyMethod = "close")
    public AnthropicClient anthropicClient(ClaudeProperties properties) {
        return AnthropicOkHttpClient.builder()
            .apiKey(properties.apiKey())
            .timeout(properties.timeout())
            .maxRetries(properties.maxRetries())
            .build();
    }
}
