package com.lilsawe.orderdemo.api.dto;

import com.lilsawe.orderdemo.domain.OrderEntity;

import java.time.Instant;

public record OrderResponse(String orderNo, long amountCent, String status, Instant createdAt) {

    public static OrderResponse from(OrderEntity order) {
        return new OrderResponse(order.getOrderNo(), order.getAmountCent(), order.getStatus().name(), order.getCreatedAt());
    }
}
