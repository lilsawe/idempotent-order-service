package com.lilsawe.kit.autoconfigure;

import com.lilsawe.kit.idempotency.IdempotencyStore;
import com.lilsawe.kit.idempotency.InMemoryIdempotencyStore;
import com.lilsawe.kit.idempotency.RedisIdempotencyStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 自动装配测试：引入依赖即可用，无需业务侧手写 @Bean。
 */
class KitAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(KitAutoConfiguration.class));

    @Test
    @DisplayName("默认提供内存实现（零依赖即可用）")
    void providesInMemoryStoreByDefault() {
        runner.run(context -> assertThat(context)
                .hasSingleBean(IdempotencyStore.class)
                .getBean(IdempotencyStore.class)
                .isInstanceOf(InMemoryIdempotencyStore.class));
    }

    @Test
    @DisplayName("配置 store=redis 且存在 StringRedisTemplate 时切换到 Redis 实现")
    void switchesToRedisWhenConfigured() {
        runner.withPropertyValues("kit.idempotency.store=redis")
                .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
                .run(context -> assertThat(context)
                        .hasSingleBean(IdempotencyStore.class)
                        .getBean(IdempotencyStore.class)
                        .isInstanceOf(RedisIdempotencyStore.class));
    }

    @Test
    @DisplayName("业务自定义 IdempotencyStore 时自动装配让位")
    void backsOffWhenUserProvidesBean() {
        runner.withBean(IdempotencyStore.class, InMemoryIdempotencyStore::new)
                .run(context -> assertThat(context).hasSingleBean(IdempotencyStore.class));
    }
}
