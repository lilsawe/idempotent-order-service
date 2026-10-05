package com.lilsawe.order.idempotency;

import java.util.Optional;

/**
 * 幂等键存储抽象。
 *
 * <p>生产环境用 Redis（SETNX + TTL）保证多实例下的原子性；
 * 本地开发 / 单元测试用内存实现，避免依赖外部组件。
 */
public interface IdempotencyStore {

    /** 查询幂等键对应的订单号。 */
    Optional<String> find(String key);

    /** 占位：key 不存在时写入并返回 true；已存在返回 false（等价 Redis SETNX）。 */
    boolean putIfAbsent(String key, String orderNo);

    /** 清空（仅内存实现支持）。 */
    void clear();
}
