package com.lilsawe.kit.idempotency;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Redis 实现的行为测试（用 mock 替代真实 Redis，CI 无需中间件）。 */
@ExtendWith(MockitoExtension.class)
class RedisIdempotencyStoreTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Test
    @DisplayName("find 读取带前缀的键")
    void findReadsPrefixedKey() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("idem:K1")).thenReturn("OD-1");

        Optional<String> found = new RedisIdempotencyStore(redisTemplate).find("K1");

        assertEquals(Optional.of("OD-1"), found);
    }

    @Test
    @DisplayName("putIfAbsent 使用 SET NX EX（带 TTL）")
    void putIfAbsentUsesSetIfAbsentWithTtl() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("idem:K1"), eq("OD-1"), any(Duration.class))).thenReturn(true);

        assertTrue(new RedisIdempotencyStore(redisTemplate).putIfAbsent("K1", "OD-1"));
        verify(valueOperations).setIfAbsent(eq("idem:K1"), eq("OD-1"), any(Duration.class));
    }

    @Test
    @DisplayName("键已存在时 putIfAbsent 返回 false")
    void putIfAbsentReturnsFalseWhenPresent() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(any(), any(), any(Duration.class))).thenReturn(false);

        assertFalse(new RedisIdempotencyStore(redisTemplate).putIfAbsent("K1", "OD-1"));
    }

    @Test
    @DisplayName("clear 为安全空操作，不清空线上数据")
    void clearIsNoop() {
        new RedisIdempotencyStore(redisTemplate).clear();
    }
}
