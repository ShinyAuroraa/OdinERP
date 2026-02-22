package com.odin.wms;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Redis Connection — Integration Tests")
class RedisIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Test
    @DisplayName("RedisTemplate can write and read String values")
    void redisTemplateCanWriteAndReadString() {
        String key = "test:bootstrap:string";
        String value = "odin-wms-test";

        redisTemplate.opsForValue().set(key, value, Duration.ofSeconds(10));
        Object result = redisTemplate.opsForValue().get(key);

        assertThat(result).isNotNull();
        assertThat(result.toString()).isEqualTo(value);

        redisTemplate.delete(key);
    }

    @Test
    @DisplayName("StringRedisTemplate can write and read values")
    void stringRedisTemplateCanWriteAndRead() {
        String key = "test:bootstrap:str-template";
        String value = "wms-active";

        stringRedisTemplate.opsForValue().set(key, value, Duration.ofSeconds(10));
        String result = stringRedisTemplate.opsForValue().get(key);

        assertThat(result).isEqualTo(value);

        stringRedisTemplate.delete(key);
    }

    @Test
    @DisplayName("Redis connection is alive (ping)")
    void redisConnectionIsAlive() {
        Boolean hasKey = redisTemplate.hasKey("__ping__");
        // If Redis is not connected this will throw an exception, not return null
        assertThat(hasKey).isNotNull();
    }
}
