package com.lilsawe.kit.idempotency;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** 内存实现：单实例 / 测试用，CAS 语义与 Redis SETNX 一致。 */
public class InMemoryIdempotencyStore implements IdempotencyStore {

    private final ConcurrentMap<String, String> store = new ConcurrentHashMap<>();

    @Override
    public Optional<String> find(String key) {
        return Optional.ofNullable(store.get(key));
    }

    @Override
    public boolean putIfAbsent(String key, String value) {
        return store.putIfAbsent(key, value) == null;
    }

    @Override
    public void clear() {
        store.clear();
    }
}
