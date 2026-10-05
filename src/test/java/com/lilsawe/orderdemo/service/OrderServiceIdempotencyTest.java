package com.lilsawe.orderdemo.service;

import com.lilsawe.orderdemo.domain.OrderEntity;
import com.lilsawe.orderdemo.domain.OrderRepository;
import com.lilsawe.orderdemo.idempotency.IdempotencyStore;
import com.lilsawe.orderdemo.idempotency.InMemoryIdempotencyStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderServiceIdempotencyTest {

    private OrderRepository orderRepository;
    private IdempotencyStore idempotencyStore;
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        idempotencyStore = new InMemoryIdempotencyStore();
        orderService = new OrderService(orderRepository, idempotencyStore, new OrderNoGenerator());
        when(orderRepository.save(any(OrderEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("同一个 Idempotency-Key 重复下单只落库一次，返回同一笔订单")
    void repeatedKeyCreatesSingleOrder() {
        OrderEntity first = orderService.create("KEY-1", 9900L);
        when(orderRepository.findByOrderNo(first.getOrderNo())).thenReturn(Optional.of(first));

        OrderEntity replay = orderService.create("KEY-1", 9900L);

        assertEquals(first.getOrderNo(), replay.getOrderNo());
        verify(orderRepository, times(1)).save(any(OrderEntity.class));
    }

    @Test
    @DisplayName("不同 Idempotency-Key 生成不同订单")
    void differentKeysCreateDifferentOrders() {
        OrderEntity first = orderService.create("KEY-A", 1000L);
        OrderEntity second = orderService.create("KEY-B", 1000L);

        assertNotEquals(first.getOrderNo(), second.getOrderNo());
        verify(orderRepository, times(2)).save(any(OrderEntity.class));
    }

    @Test
    @DisplayName("幂等键存储未命中时，用数据库唯一索引兜底，不再新建订单")
    void databaseUniqueIndexFallback() {
        OrderEntity existing = new OrderEntity("OD-EXISTING", "KEY-DB", 500L);
        when(orderRepository.findByIdempotencyKey("KEY-DB")).thenReturn(Optional.of(existing));

        OrderEntity result = orderService.create("KEY-DB", 500L);

        assertEquals("OD-EXISTING", result.getOrderNo());
        verify(orderRepository, never()).save(any(OrderEntity.class));
    }

    @Test
    @DisplayName("Idempotency-Key 为空直接拒绝")
    void blankKeyRejected() {
        assertThrows(IllegalArgumentException.class, () -> orderService.create("  ", 100L));
    }

    @Test
    @DisplayName("金额必须大于 0")
    void nonPositiveAmountRejected() {
        assertThrows(IllegalArgumentException.class, () -> orderService.create("KEY-2", 0L));
    }
}
