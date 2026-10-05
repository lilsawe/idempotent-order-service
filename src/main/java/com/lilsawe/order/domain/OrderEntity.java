package com.lilsawe.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.time.Instant;

/**
 * 订单实体。
 *
 * <p>数据库层用两个唯一索引兜底：order_no 唯一、idempotency_key 唯一。
 * 这样即使幂等键存储在并发下出现漏洞，数据库也不会写入重复订单。
 */
@Entity
@Table(name = "orders", uniqueConstraints = {
        @UniqueConstraint(name = "uk_orders_order_no", columnNames = "order_no"),
        @UniqueConstraint(name = "uk_orders_idempotency_key", columnNames = "idempotency_key")
})
public class OrderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_no", nullable = false, length = 64)
    private String orderNo;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "amount_cent", nullable = false)
    private long amountCent;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private OrderStatus status = OrderStatus.CREATED;

    /** 乐观锁版本号，防止并发状态覆盖。 */
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected OrderEntity() {
        // JPA 需要无参构造
    }

    public OrderEntity(String orderNo, String idempotencyKey, long amountCent) {
        this.orderNo = orderNo;
        this.idempotencyKey = idempotencyKey;
        this.amountCent = amountCent;
    }

    public void transitionTo(OrderStatus next) {
        OrderStateMachine.assertTransition(this.status, next);
        this.status = next;
    }

    public Long getId() {
        return id;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public long getAmountCent() {
        return amountCent;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
