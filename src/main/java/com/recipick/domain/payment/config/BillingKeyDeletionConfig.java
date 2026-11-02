package com.recipick.domain.payment.config;

import com.recipick.infra.portone.PortOneProperties;
import java.net.http.HttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(BillingKeyDeletionProperties.class)
public class BillingKeyDeletionConfig {
    public static final String REST_CLIENT = "billingKeyDeletionRestClient";

    @Bean(REST_CLIENT)
    RestClient billingKeyDeletionRestClient(PortOneProperties portOne, BillingKeyDeletionProperties deletion) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(portOne.connectTimeout()).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(deletion.readTimeout());
        return RestClient.builder().baseUrl(portOne.baseUrl()).requestFactory(factory)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "PortOne " + portOne.apiSecret())
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE).build();
    }
}
