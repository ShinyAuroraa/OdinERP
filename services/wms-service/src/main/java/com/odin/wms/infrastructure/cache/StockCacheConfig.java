package com.odin.wms.infrastructure.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;
import java.util.Map;

/**
 * Configuração do cache Redis para saldo de estoque.
 * TTL configurável via {@code wms.cache.stock-balance.ttl-minutes} (padrão: 5 minutos).
 * Implementa {@link CachingConfigurer} para degradação graciosa — erros de cache são
 * logados mas não propagados, mantendo o sistema funcional mesmo sem Redis.
 */
@Slf4j
@Configuration
@EnableCaching
@RequiredArgsConstructor
public class StockCacheConfig implements CachingConfigurer {

    static final String CACHE_STOCK_BALANCE = "stockBalance";

    @Value("${wms.cache.stock-balance.ttl-minutes:5}")
    private int ttlMinutes;

    private final RedisConnectionFactory redisConnectionFactory;

    /**
     * CacheManager com TTL configurável para o cache de saldo de estoque.
     * Usa ObjectMapper customizado com JavaTimeModule para suporte a java.time.Instant.
     */
    @Bean
    @Override
    public CacheManager cacheManager() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        RedisCacheConfiguration stockBalanceConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(ttlMinutes))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer(objectMapper)))
                .disableCachingNullValues();

        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(stockBalanceConfig)
                .withInitialCacheConfigurations(Map.of(CACHE_STOCK_BALANCE, stockBalanceConfig))
                .build();
    }

    /**
     * Degradação graciosa: erros de cache são logados mas nunca propagam para o caller.
     * Se Redis estiver indisponível, as queries vão direto ao PostgreSQL sem exception.
     */
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException ex, Cache cache, Object key) {
                log.warn("Cache GET error em '{}' key='{}': {}", cache.getName(), key, ex.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException ex, Cache cache, Object key, Object value) {
                log.warn("Cache PUT error em '{}' key='{}': {}", cache.getName(), key, ex.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException ex, Cache cache, Object key) {
                log.warn("Cache EVICT error em '{}' key='{}': {}", cache.getName(), key, ex.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException ex, Cache cache) {
                log.warn("Cache CLEAR error em '{}': {}", cache.getName(), ex.getMessage());
            }
        };
    }
}
