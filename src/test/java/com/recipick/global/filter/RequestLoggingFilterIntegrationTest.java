package com.recipick.global.filter;

import static org.assertj.core.api.Assertions.assertThat;

import com.recipick.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

class RequestLoggingFilterIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("보안 필터가 거절한 요청(401)에도 X-Request-Id가 담긴다")
    void traceIdHeader_existsOnUnauthorized() throws Exception {
        // given
        String path = "/api/products";

        // when
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get(path)).andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        assertThat(result.getResponse().getHeader(RequestLoggingFilter.TRACE_ID_HEADER)).isNotBlank();
    }

    @Test
    @DisplayName("공개 경로 응답에도 X-Request-Id가 담긴다")
    void traceIdHeader_existsOnPublicApi() throws Exception {
        // given
        String path = "/api/categories";

        // when
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get(path)).andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getHeader(RequestLoggingFilter.TRACE_ID_HEADER)).isNotBlank();
    }
}
