package com.lilsawe.order.api.dto;

import jakarta.validation.constraints.Positive;

/** 下单请求体。金额单位：分（避免浮点误差）。 */
public record CreateOrderRequest(@Positive(message = "amountCent 必须大于 0") long amountCent) {
}
