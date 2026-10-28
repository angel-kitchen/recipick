package com.recipick.global.filter;

import com.recipick.IntegrationTestSupport;
import com.recipick.infra.redis.RedisRateLimitStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = {
    "recipick.rate-limit.enabled=true",
    "recipick.rate-limit.api-limit=3",
    "recipick.rate-limit.auth-limit=2"
})
class RateLimitFilterTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private StringRedisTemplate redisTemplate;

    @MockitoSpyBean
    private RedisRateLimitStore store;

    // Redis 컨테이너를 테스트 클래스끼리 공유하므로 이전 카운터를 지우고 시작한다.
    @BeforeEach
    void clearCounters(){
        Set<String> keys = redisTemplate.keys("recipick:rate:*");
        if(keys != null && !keys.isEmpty()){
            redisTemplate.delete(keys);
        }
    }

    @Test
    @DisplayName("한도 안의 요청은 통과한다")
    void underLimit_passes() throws Exception{
        for(int i = 0; i < 3; i++){
            mockMvc.perform(get("/api/categories")).andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("한도를 넘으면 429와 COMMON-012를 돌려준다")
    void overLimit_returns429() throws Exception{
        for(int i = 0; i < 3; i++){
            mockMvc.perform(get("/api/categories")).andExpect(status().isOk());
        }
        mockMvc.perform(get("/api/categories"))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.code").value("COMMON-012"));
    }

    @Test
    @DisplayName("로그인류는 일반 API와 따로 세고 더 낮은 한도를 쓴다")
    void authPolicy_isCountedSeparately() throws Exception {
        for(int i = 0; i < 2; i++){
            mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        }

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isTooManyRequests());
        // 로그인 줄이 막혀도 일반 API 줄은 그대로다.
        mockMvc.perform(get("/api/categories")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("웹훅은 한도를 넘겨도 막지 않는다")
    void webhook_isNotLimited() throws Exception {
        for(int i = 0; i < 5; i++){
            int httpStatus = mockMvc.perform(post("/api/webhooks/test")).andReturn().getResponse().getStatus();
            assertThat(httpStatus).isNotEqualTo(429);
        }
    }

    @Test
    @DisplayName("Redis 카운터가 실패하면 제한 없이 통과한다")
    void redisFailure_failsOpen() throws Exception {
        doThrow(new RuntimeException("redis down")).when(store).increment(any(), any());

        for(int i = 0; i < 5; i++){
            mockMvc.perform(get("/api/categories")).andExpect(status().isOk());
        }
    }
}
