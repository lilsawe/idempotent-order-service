package com.lilsawe.order.service;

import com.lilsawe.order.domain.OrderEntity;
import com.lilsawe.order.domain.OrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 并发幂等验证：200 个线程用同一个 Idempotency-Key 同时下单，
 * 期望只落库 1 笔订单，且所有线程拿到同一个订单号。
 */
@SpringBootTest
class IdempotencyConcurrencyTest {

    private static final int THREADS = 200;
    private static final String KEY = "CONCURRENT-KEY-1";

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Test
    @DisplayName("200 并发同 key 下单：只产生 1 笔订单，返回结果一致")
    void concurrentSameKeyCreatesExactlyOneOrder() throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREADS);
        Set<String> orderNos = ConcurrentHashMap.newKeySet();
        AtomicInteger failures = new AtomicInteger();
        java.util.List<String> errors = java.util.Collections.synchronizedList(new java.util.ArrayList<>());

        for (int i = 0; i < THREADS; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    OrderEntity order = orderService.create(KEY, 8800L);
                    orderNos.add(order.getOrderNo());
                } catch (Exception ex) {
                    failures.incrementAndGet();
                    if (errors.size() < 5) {
                        errors.add(ex.getClass().getName() + ": " + ex.getMessage());
                    }
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertTrue(done.await(60, TimeUnit.SECONDS), "并发下单未在 60s 内完成");
        pool.shutdownNow();

        List<OrderEntity> all = orderRepository.findAll().stream()
                .filter(order -> KEY.equals(order.getIdempotencyKey()))
                .toList();

        assertEquals(0, failures.get(), "并发请求出现异常，首个异常: " + (errors.isEmpty() ? "无" : errors.get(0)) + "（共 " + errors.size() + " 个）");
        assertEquals(1, all.size(), "同一幂等键只应落库 1 笔订单");
        assertEquals(1, orderNos.size(), "所有线程应拿到同一个订单号");
        assertEquals(all.get(0).getOrderNo(), orderNos.iterator().next());
    }
}
