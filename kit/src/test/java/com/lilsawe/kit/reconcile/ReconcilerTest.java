package com.lilsawe.kit.reconcile;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReconcilerTest {

    record LocalOrder(String orderNo, long amountCent) {
    }

    record ChannelSettlement(String orderNo, long amountCent) {
    }

    @Test
    @DisplayName("四类结果：一致 / 金额不一致 / 左侧多 / 右侧多")
    void classifiesAllCases() {
        List<LocalOrder> local = List.of(
                new LocalOrder("A", 1000),
                new LocalOrder("B", 2000),
                new LocalOrder("C", 3000));
        List<ChannelSettlement> channel = List.of(
                new ChannelSettlement("A", 1000),
                new ChannelSettlement("B", 1500),
                new ChannelSettlement("D", 4000));

        ReconcileReport report = Reconciler.compare(
                local, channel, LocalOrder::orderNo, LocalOrder::amountCent,
                ChannelSettlement::orderNo, ChannelSettlement::amountCent);

        assertEquals(3, report.leftCount());
        assertEquals(3, report.rightCount());
        assertEquals(1, report.matched());
        assertEquals(3, report.diffs().size());
        assertTrue(report.diffs().stream().anyMatch(d ->
                d.type() == DiffType.AMOUNT_MISMATCH && "B".equals(d.key()) && d.rightAmount() == 1500L));
        assertTrue(report.diffs().stream().anyMatch(d ->
                d.type() == DiffType.MISSING_IN_RIGHT && "C".equals(d.key())));
        assertTrue(report.diffs().stream().anyMatch(d ->
                d.type() == DiffType.MISSING_IN_LEFT && "D".equals(d.key())));
    }

    @Test
    @DisplayName("完全一致时 balanced() 为 true")
    void balancedWhenIdentical() {
        ReconcileReport report = Reconciler.compare(
                List.of(new LocalOrder("X", 700)),
                List.of(new ChannelSettlement("X", 700)),
                LocalOrder::orderNo, LocalOrder::amountCent,
                ChannelSettlement::orderNo, ChannelSettlement::amountCent);

        assertTrue(report.balanced());
        assertEquals(1, report.matched());
    }

    @Test
    @DisplayName("同类型集合的便捷重载 + compareIndexed")
    void convenienceOverloads() {
        ReconcileReport same = Reconciler.compare(
                List.of(new LocalOrder("A", 1)), List.of(new LocalOrder("A", 1)),
                LocalOrder::orderNo, LocalOrder::amountCent);
        assertTrue(same.balanced());

        ReconcileReport indexed = Reconciler.compareIndexed(Map.of("A", 1L), Map.of("A", 2L));
        assertFalse(indexed.balanced());
    }


    @Test
    @DisplayName("null 与空集合都能安全处理")
    void handlesNullAndEmptyCollections() {
        ReconcileReport empty = Reconciler.compare(
                List.of(), List.of(),
                LocalOrder::orderNo, LocalOrder::amountCent,
                ChannelSettlement::orderNo, ChannelSettlement::amountCent);

        assertEquals(0, empty.leftCount());
        assertEquals(0, empty.rightCount());
        assertTrue(empty.balanced());

        ReconcileReport withNull = Reconciler.compare(
                null, null,
                LocalOrder::orderNo, LocalOrder::amountCent,
                ChannelSettlement::orderNo, ChannelSettlement::amountCent);

        assertTrue(withNull.balanced());

        assertTrue(Reconciler.compareIndexed(Map.of(), Map.of()).balanced());
    }

    @Test
    @DisplayName("重复业务键以最后一条为准")
    void duplicateKeysKeepLastValue() {
        ReconcileReport report = Reconciler.compare(
                List.of(new LocalOrder("A", 100), new LocalOrder("A", 200)),
                List.of(new ChannelSettlement("A", 200)),
                LocalOrder::orderNo, LocalOrder::amountCent,
                ChannelSettlement::orderNo, ChannelSettlement::amountCent);

        assertEquals(1, report.leftCount());
        assertTrue(report.balanced(), "去重后应以最后一条为准");
    }

    @Test
    @DisplayName("差异明细保留两侧金额，便于定位")
    void diffCarriesBothAmounts() {
        ReconcileReport report = Reconciler.compare(
                List.of(new LocalOrder("A", 100)),
                List.of(new ChannelSettlement("A", 150)),
                LocalOrder::orderNo, LocalOrder::amountCent,
                ChannelSettlement::orderNo, ChannelSettlement::amountCent);

        ReconcileDiff diff = report.diffs().get(0);
        assertEquals(DiffType.AMOUNT_MISMATCH, diff.type());
        assertEquals(100L, diff.leftAmount());
        assertEquals(150L, diff.rightAmount());
    }
}
