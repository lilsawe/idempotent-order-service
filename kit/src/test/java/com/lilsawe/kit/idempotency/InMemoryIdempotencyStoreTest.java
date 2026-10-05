package com.lilsawe.kit.idempotency;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryIdempotencyStoreTest {

    private final InMemoryIdempotencyStore store = new InMemoryIdempotencyStore();

    @Test
    @DisplayName("未写入时 find 返回空")
    void findReturnsEmptyWhenAbsent() {
        assertEquals(Optional.empty(), store.find("missing"));
    }

    @Test
    @DisplayName("putIfAbsent 首次返回 true，之后返回 false 且不覆盖旧值")
    void putIfAbsentIsAtomicAndKeepsFirstValue() {
        assertTrue(store.putIfAbsent("k1", "first"));
        assertFalse(store.putIfAbsent("k1", "second"));

        assertEquals(Optional.of("first"), store.find("k1"));
    }

    @Test
    @DisplayName("clear 清空所有键")
    void clearRemovesEverything() {
        store.putIfAbsent("k1", "v1");
        store.clear();

        assertEquals(Optional.empty(), store.find("k1"));
    }

    @Test
    @DisplayName("并发下同一个键只有一个调用方抢到")
    void onlyOneWinnerUnderConcurrency() throws InterruptedException {
        int threads = 32;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger winners = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            final int index = i;
            pool.submit(() -> {
                try {
                    start.await();
                    if (store.putIfAbsent("shared", "value-" + index)) {
                        winners.incrementAndGet();
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS));
        pool.shutdownNow();

        assertEquals(1, winners.get(), "同一幂等键只能有一个赢家");
        assertTrue(store.find("shared").isPresent());
    }
}
