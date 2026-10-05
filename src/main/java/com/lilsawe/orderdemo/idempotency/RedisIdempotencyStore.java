package com.lilsawe.orderdemo.idempotency;

import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * 基于 Redis 的幂等键存储（profile = redis 时生效）。
 *
 * <p>setIfAbsent 即 Redis 的 SET key value NX EX ttl，保证多实例并发下只有一个请求能抢到 key。
 */
@Component
@Profile("redis")
public class RedisIdempotencyStore implements IdempotencyStore {

    private static final Duration TTL = Duration.ofHours(24);
    private static final String PREFIX = "idem:order:";

    private final StringRedisTemplate redisTemplate;

    public RedisIdempotencyStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Optional<String> find(String key) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(PREFIX + key));
    }

    @Override
    public boolean putIfAbsent(String key, String orderNo) {
        Boolean ok = redisTemplate.opsForValue().setIfAbsent(PREFIX + key, orderNo, TTL);
        return Boolean.TRUE.equals(ok);
    }

    @Override
    public void clear() {
        // 演示项目不做全量清理
    }
}
