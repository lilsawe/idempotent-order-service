package com.lilsawe.kit.autoconfigure;

import com.lilsawe.kit.idempotency.IdempotencyStore;
import com.lilsawe.kit.idempotency.InMemoryIdempotencyStore;
import com.lilsawe.kit.idempotency.RedisIdempotencyStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 自动装配：引入依赖即获得一个 {@link IdempotencyStore}，无需在业务代码里手写 @Bean。
 *
 * <p>通过配置选择实现：
 * <pre>
 * kit:
 *   idempotency:
 *     store: memory   # 默认；redis 则使用 Redis 实现
 * </pre>
 */
@AutoConfiguration
public class KitAutoConfiguration {

    /** 默认实现：内存（零依赖，适合本地开发与测试）。 */
    @Bean
    @ConditionalOnMissingBean(IdempotencyStore.class)
    @ConditionalOnProperty(prefix = "kit.idempotency", name = "store", havingValue = "memory", matchIfMissing = true)
    public IdempotencyStore inMemoryIdempotencyStore() {
        return new InMemoryIdempotencyStore();
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(StringRedisTemplate.class)
    @ConditionalOnProperty(prefix = "kit.idempotency", name = "store", havingValue = "redis")
    static class RedisIdempotencyConfiguration {

        @Bean
        @ConditionalOnMissingBean(IdempotencyStore.class)
        IdempotencyStore redisIdempotencyStore(StringRedisTemplate redisTemplate) {
            return new RedisIdempotencyStore(redisTemplate);
        }
    }
}
