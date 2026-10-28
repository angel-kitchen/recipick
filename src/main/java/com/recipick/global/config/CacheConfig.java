package com.recipick.global.config;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.recipick.domain.product.dto.CategoryResponse;

import java.time.Duration;
import java.util.List;

import com.recipick.domain.product.dto.OpenMartResponse;
import com.recipick.domain.product.dto.ProductSearchResponse;
import com.recipick.global.response.PageResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;


/**
 * 조회 캐시(Redis). 캐시 이름마다 TTL과 값 타입을 정해 둔다.
 * 키는 {@code recipick:cache:} 접두어를 붙여 로그인 제한 등 다른 Redis 데이터와 섞이지 않게 한다.
 * {@code recipick.cache.enabled=false} 이면 캐시를 끈다(기존 통합 테스트는 끈 채로 돌린다.
 */

@Configuration
@EnableCaching
@ConditionalOnProperty(name = "recipick.cache.enabled", havingValue = "true", matchIfMissing = true)
public class CacheConfig implements CachingConfigurer {

    public static final String CATEGORIES = "categories";
    public static final String OPEN_MARTS = "openMarts";
    public static final String PRODUCT_SEARCH = "productSearch";

    private static final String KEY_PREFIX = "recipick:cache:";
    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory){
        ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();
        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
            .prefixCacheNameWith(KEY_PREFIX)
            .disableCachingNullValues();

        JavaType categories = mapper.getTypeFactory().constructCollectionType(List.class, CategoryResponse.class);
        JavaType openMarts = mapper.getTypeFactory().constructCollectionType(List.class, OpenMartResponse.class);
        JavaType productSearch = mapper.getTypeFactory()
            .constructParametricType(PageResponse.class, ProductSearchResponse.class);

        return RedisCacheManager.builder(connectionFactory)
            .withCacheConfiguration(CATEGORIES, typed(base, mapper, categories, Duration.ofMinutes(10)))
            .withCacheConfiguration(OPEN_MARTS, typed(base, mapper, openMarts, Duration.ofMinutes(5)))
            .withCacheConfiguration(PRODUCT_SEARCH, typed(base, mapper, productSearch, Duration.ofSeconds(30)))
            .disableCreateOnMissingCache()
            .enableStatistics()
            .build();
    }

    private static RedisCacheConfiguration typed(RedisCacheConfiguration base, ObjectMapper mapper, JavaType type, Duration ttl){
        return base.entryTtl(ttl)
            .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new Jackson2JsonRedisSerializer<>(mapper,type)));
    }

    /** Redis에 문제가 있어도 캐시를 못 쓸 뿐 API는 DB로 정상 응답해야 한다. */
    @Override
    public CacheErrorHandler errorHandler(){
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException e, Cache cache, Object key){
                log.warn("캐시 조회 실패, DB로 대체합니다. cache{}", cache.getName(), e);
            }

            @Override
            public void handleCachePutError(RuntimeException e, Cache cache, Object key, Object value){
                log.warn("캐시 저장 실패. cache{}", cache.getName(), e);
            }

            @Override
            public void handleCacheEvictError(RuntimeException e, Cache cache, Object key){
                log.warn("캐시 삭제 실패. cache{}", cache.getName(), e);
            }

            @Override
            public void handleCacheClearError(RuntimeException e, Cache cache){
                log.warn("캐시 비우기 실패. cache={}", cache.getName(), e);
            }
        };
    }
}
