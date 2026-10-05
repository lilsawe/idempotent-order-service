package com.lilsawe.orderdemo.idempotency;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
@Profile("!redis")
public class InMemoryIdempotencyStore implements IdempotencyStore {

    private final ConcurrentMap<String, String> store = new ConcurrentHashMap<>();

    @Override
    public Optional<String> find(String key) {
        return Optional.ofNullable(store.get(key));
    }

    @Override
    public boolean putIfAbsent(String key, String orderNo) {
        return store.putIfAbsent(key, orderNo) == null;
    }

    @Override
    public void clear() {
        store.clear();
    }
}
