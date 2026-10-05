package com.lilsawe.orderdemo.service;

import com.lilsawe.orderdemo.domain.OrderEntity;
import com.lilsawe.orderdemo.domain.OrderRepository;
import com.lilsawe.orderdemo.domain.OrderStatus;
import com.lilsawe.orderdemo.idempotency.IdempotencyStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 订单服务：接口幂等的三层防线
 *
 * <ol>
 *     <li>幂等键存储（Redis SETNX / 内存 CAS）—— 并发下的第一道闸门</li>
 *     <li>数据库唯一索引 idempotency_key —— 存储层兜底，绝不写入重复订单</li>
 *     <li>重放时直接返回首次创建的订单 —— 调用方拿到相同结果</li>
 * </ol>
 */
@Service
public class OrderService {

    /** 未抢到幂等键时的最大回查次数。 */
    private static final int MAX_RETRY = 50;

    /** 回查间隔（毫秒）。 */
    private static final long RETRY_INTERVAL_MILLIS = 10L;


    private final OrderRepository orderRepository;
    private final IdempotencyStore idempotencyStore;
    private final OrderNoGenerator orderNoGenerator;

    public OrderService(OrderRepository orderRepository,
                        IdempotencyStore idempotencyStore,
                        OrderNoGenerator orderNoGenerator) {
        this.orderRepository = orderRepository;
        this.idempotencyStore = idempotencyStore;
        this.orderNoGenerator = orderNoGenerator;
    }

    @Transactional
    public OrderEntity create(String idempotencyKey, long amountCent) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key 不能为空");
        }
        if (amountCent <= 0) {
            throw new IllegalArgumentException("amountCent 必须大于 0");
        }

        // 1) 幂等键命中：直接返回首次创建的订单（重放）
        Optional<String> cachedOrderNo = idempotencyStore.find(idempotencyKey);
        if (cachedOrderNo.isPresent()) {
            return loadByOrderNo(cachedOrderNo.get(), idempotencyKey);
        }

        // 2) 数据库唯一索引兜底：进程重启后内存丢失也能识别重复请求
        Optional<OrderEntity> existing = orderRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            idempotencyStore.putIfAbsent(idempotencyKey, existing.get().getOrderNo());
            return existing.get();
        }

        // 3) 抢占幂等键：抢不到说明并发请求已经在处理，短暂回查返回同一订单
        String orderNo = orderNoGenerator.next();
        if (!idempotencyStore.putIfAbsent(idempotencyKey, orderNo)) {
            return awaitExistingOrder(idempotencyKey, idempotencyStore.find(idempotencyKey).orElse(orderNo));
        }

        // 4) 落库
        return orderRepository.save(new OrderEntity(orderNo, idempotencyKey, amountCent));
    }

    @Transactional(readOnly = true)
    public OrderEntity getByOrderNo(String orderNo) {
        return orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new IllegalArgumentException("订单不存在: " + orderNo));
    }

    @Transactional
    public OrderEntity changeStatus(String orderNo, OrderStatus next) {
        OrderEntity order = getByOrderNo(orderNo);
        order.transitionTo(next);
        return orderRepository.save(order);
    }

    /**
     * 并发下未抢到幂等键的请求：赢家的订单可能还没提交，直接查会读不到，
     * 因此做有限次、带间隔的回查（生产可换成等待+超时或返回 202 处理中）。
     */
    private OrderEntity awaitExistingOrder(String idempotencyKey, String orderNo) {
        for (int attempt = 0; attempt < MAX_RETRY; attempt++) {
            Optional<OrderEntity> byOrderNo = orderRepository.findByOrderNo(orderNo);
            if (byOrderNo.isPresent()) {
                return byOrderNo.get();
            }
            Optional<OrderEntity> byKey = orderRepository.findByIdempotencyKey(idempotencyKey);
            if (byKey.isPresent()) {
                return byKey.get();
            }
            sleepQuietly();
        }
        throw new IllegalStateException("幂等键 " + idempotencyKey + " 正在处理中，请稍后重试");
    }

    private void sleepQuietly() {
        try {
            Thread.sleep(RETRY_INTERVAL_MILLIS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待幂等结果被中断", ex);
        }
    }

    private OrderEntity loadByOrderNo(String orderNo, String idempotencyKey) {
        return orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new IllegalStateException(
                        "幂等键 " + idempotencyKey + " 已占用，但订单 " + orderNo + " 不存在"));
    }
}
