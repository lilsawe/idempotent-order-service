package com.lilsawe.orderdemo.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderStateMachineTest {

    @Test
    @DisplayName("待支付订单可以支付或取消")
    void createdCanPayOrCancel() {
        assertTrue(OrderStateMachine.canTransition(OrderStatus.CREATED, OrderStatus.PAID));
        assertTrue(OrderStateMachine.canTransition(OrderStatus.CREATED, OrderStatus.CANCELLED));
    }

    @Test
    @DisplayName("已支付订单可以发货或取消")
    void paidCanShipOrCancel() {
        assertTrue(OrderStateMachine.canTransition(OrderStatus.PAID, OrderStatus.SHIPPED));
        assertTrue(OrderStateMachine.canTransition(OrderStatus.PAID, OrderStatus.CANCELLED));
    }

    @Test
    @DisplayName("已发货、已取消是终态，不能再流转")
    void terminalStates() {
        assertFalse(OrderStateMachine.canTransition(OrderStatus.SHIPPED, OrderStatus.CANCELLED));
        assertFalse(OrderStateMachine.canTransition(OrderStatus.CANCELLED, OrderStatus.PAID));
    }

    @Test
    @DisplayName("重复流转到同一状态视为幂等，不报错")
    void sameStatusIsIdempotent() {
        assertTrue(OrderStateMachine.canTransition(OrderStatus.PAID, OrderStatus.PAID));
    }

    @Test
    @DisplayName("非法流转抛出 IllegalStateException")
    void illegalTransitionThrows() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> OrderStateMachine.assertTransition(OrderStatus.CREATED, OrderStatus.SHIPPED));
        assertTrue(ex.getMessage().contains("非法状态流转"));
    }
}
