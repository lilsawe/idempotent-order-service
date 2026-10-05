package com.lilsawe.orderdemo.service;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/** 订单号生成器：时间戳 + 自增序号 + 随机数（演示用，生产建议用发号器或雪花算法）。 */
@Component
public class OrderNoGenerator {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final AtomicInteger sequence = new AtomicInteger();

    public String next() {
        int seq = Math.abs(sequence.incrementAndGet() % 1000);
        int random = ThreadLocalRandom.current().nextInt(1000, 9999);
        return "OD" + LocalDateTime.now().format(FORMATTER) + String.format("%03d", seq) + random;
    }
}
