package com.lilsawe.kit.idempotency;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Optional;

/**
 * Redis 实现：setIfAbsent 即 SET key value NX EX ttl，多实例并发下只有一个请求能抢到 key。
 */
public class RedisIdempotencyStore implements IdempotencyStore {

    private static final String DEFAULT_PREFIX = "idem:";

    private final StringRedisTemplate redisTemplate;
    private final String prefix;
    private final Duration ttl;

    public RedisIdempotencyStore(StringRedisTemplate redisTemplate) {
        this(redisTemplate, DEFAULT_PREFIX, Duration.ofHours(24));
    }

    public RedisIdempotencyStore(StringRedisTemplate redisTemplate, String prefix, Duration ttl) {
        this.redisTemplate = redisTemplate;
        this.prefix = prefix;
        this.ttl = ttl;
    }

    @Override
    public Optional<String> find(String key) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(prefix + key));
    }

    @Override
    public boolean putIfAbsent(String key, String value) {
        Boolean ok = redisTemplate.opsForValue().setIfAbsent(prefix + key, value, ttl);
        return Boolean.TRUE.equals(ok);
    }

    @Override
    public void clear() {
        // Redis 实现不做全量清理，避免误删线上数据
    }
}
