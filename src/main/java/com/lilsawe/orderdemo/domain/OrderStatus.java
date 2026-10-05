package com.lilsawe.orderdemo.domain;

/**
 * 订单状态。流转规则见 {@link OrderStateMachine}。
 */
public enum OrderStatus {
    CREATED,
    PAID,
    SHIPPED,
    CANCELLED
}
