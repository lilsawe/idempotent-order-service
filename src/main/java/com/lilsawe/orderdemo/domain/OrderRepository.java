package com.lilsawe.orderdemo.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrderRepository extends JpaRepository<OrderEntity, Long> {

    Optional<OrderEntity> findByOrderNo(String orderNo);

    Optional<OrderEntity> findByIdempotencyKey(String idempotencyKey);
}
