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

        // 3) 抢占幂等键：抢不到说明并发请求已经在处理，回查返回同一订单
        String orderNo = orderNoGenerator.next();
        if (!idempotencyStore.putIfAbsent(idempotencyKey, orderNo)) {
            return loadByOrderNo(idempotencyStore.find(idempotencyKey).orElse(orderNo), idempotencyKey);
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

    private OrderEntity loadByOrderNo(String orderNo, String idempotencyKey) {
        return orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new IllegalStateException(
                        "幂等键 " + idempotencyKey + " 已占用，但订单 " + orderNo + " 不存在"));
    }
}
