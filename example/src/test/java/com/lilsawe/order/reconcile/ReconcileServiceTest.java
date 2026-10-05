package com.lilsawe.order.reconcile;

import com.lilsawe.order.domain.OrderEntity;
import com.lilsawe.order.domain.OrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReconcileServiceTest {

    @Test
    @DisplayName("对账输出：一致 1 笔、金额不一致 1 笔、本地多 1 笔、渠道多 1 笔")
    void classifyDiffs() {
        OrderRepository orderRepository = mock(OrderRepository.class);
        when(orderRepository.findAll()).thenReturn(List.of(
                new OrderEntity("OD-A", "K-A", 1000L),
                new OrderEntity("OD-B", "K-B", 2000L),
                new OrderEntity("OD-C", "K-C", 3000L)));

        ChannelGateway gateway = () -> List.of(
                new ChannelOrder("OD-A", 1000L),
                new ChannelOrder("OD-B", 1500L),
                new ChannelOrder("OD-D", 4000L));

        ReconcileView report = new ReconcileService(orderRepository, gateway).reconcile();

        assertEquals(3, report.localCount());
        assertEquals(3, report.channelCount());
        assertEquals(1, report.matched());
        assertEquals(3, report.diffs().size());
        assertTrue(report.diffs().stream().anyMatch(d ->
                d.type() == DiffKind.AMOUNT_MISMATCH && "OD-B".equals(d.orderNo())));
        assertTrue(report.diffs().stream().anyMatch(d ->
                d.type() == DiffKind.MISSING_IN_CHANNEL && "OD-C".equals(d.orderNo())));
        assertTrue(report.diffs().stream().anyMatch(d ->
                d.type() == DiffKind.MISSING_IN_LOCAL && "OD-D".equals(d.orderNo())));
    }

    @Test
    @DisplayName("两侧完全一致时没有差异")
    void noDiffsWhenConsistent() {
        OrderRepository orderRepository = mock(OrderRepository.class);
        when(orderRepository.findAll()).thenReturn(List.of(new OrderEntity("OD-X", "K-X", 700L)));

        ReconcileView report = new ReconcileService(orderRepository,
                () -> List.of(new ChannelOrder("OD-X", 700L))).reconcile();

        assertEquals(1, report.matched());
        assertTrue(report.diffs().isEmpty());
    }
}
