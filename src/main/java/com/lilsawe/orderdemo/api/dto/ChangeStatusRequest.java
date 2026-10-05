package com.lilsawe.orderdemo.api.dto;

import jakarta.validation.constraints.NotBlank;

/** 状态流转请求体，status 取 CREATED / PAID / SHIPPED / CANCELLED。 */
public record ChangeStatusRequest(@NotBlank(message = "status 不能为空") String status) {
}
