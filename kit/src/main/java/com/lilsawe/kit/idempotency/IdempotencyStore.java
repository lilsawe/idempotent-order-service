package com.lilsawe.kit.idempotency;

import java.util.Optional;

/**
 * 幂等键存储抽象。
 *
 * <p>生产环境用 Redis（SETNX + TTL）保证多实例下的原子性；本地开发与单元测试用内存实现，
 * 不依赖任何外部组件。业务代码只依赖这个接口，因此可以在测试里自由替换。
 */
public interface IdempotencyStore {

    /** 查询幂等键当前绑定的值（通常是订单号 / 请求指纹）。 */
    Optional<String> find(String key);

    /** 占位：key 不存在时写入并返回 true；已存在返回 false（等价 Redis SETNX）。 */
    boolean putIfAbsent(String key, String value);

    /** 清空（仅内存实现支持；Redis 实现为空操作）。 */
    void clear();
}
