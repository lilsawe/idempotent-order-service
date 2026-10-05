package com.lilsawe.orderdemo.api;

import com.lilsawe.orderdemo.api.dto.ChangeStatusRequest;
import com.lilsawe.orderdemo.api.dto.CreateOrderRequest;
import com.lilsawe.orderdemo.api.dto.OrderResponse;
import com.lilsawe.orderdemo.domain.OrderEntity;
import com.lilsawe.orderdemo.domain.OrderStatus;
import com.lilsawe.orderdemo.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * 创建订单。必须携带 Idempotency-Key 请求头，同一个 key 重复调用只会创建一笔订单。
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse create(@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                @Valid @RequestBody CreateOrderRequest request) {
        OrderEntity order = orderService.create(idempotencyKey, request.amountCent());
        return OrderResponse.from(order);
    }

    @GetMapping("/{orderNo}")
    public OrderResponse get(@PathVariable String orderNo) {
        return OrderResponse.from(orderService.getByOrderNo(orderNo));
    }

    @PostMapping("/{orderNo}/status")
    public OrderResponse changeStatus(@PathVariable String orderNo,
                                      @Valid @RequestBody ChangeStatusRequest request) {
        OrderStatus next = OrderStatus.valueOf(request.status().trim().toUpperCase(Locale.ROOT));
        return OrderResponse.from(orderService.changeStatus(orderNo, next));
    }
}
