package com.lilsawe.order;

import com.lilsawe.order.domain.OrderEntity;
import com.lilsawe.order.domain.OrderRepository;
import com.lilsawe.order.domain.OrderStatus;
import com.lilsawe.order.service.OrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 端到端冒烟测试：真实 Spring 上下文 + H2 内存库。 */
@SpringBootTest
class OrderServiceApplicationTests {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Test
    @DisplayName("上下文可启动，且同一幂等键在真实数据库下只产生一笔订单")
    void idempotentCreateAndStatusTransition() {
        OrderEntity first = orderService.create("IT-KEY-1", 12345L);
        OrderEntity replay = orderService.create("IT-KEY-1", 12345L);

        assertEquals(first.getOrderNo(), replay.getOrderNo());
        long rows = orderRepository.findAll().stream()
                .filter(order -> "IT-KEY-1".equals(order.getIdempotencyKey()))
                .count();
        assertEquals(1L, rows);

        OrderEntity paid = orderService.changeStatus(first.getOrderNo(), OrderStatus.PAID);
        assertEquals(OrderStatus.PAID, paid.getStatus());
        assertTrue(orderRepository.findByOrderNo(first.getOrderNo()).isPresent());
    }
}
