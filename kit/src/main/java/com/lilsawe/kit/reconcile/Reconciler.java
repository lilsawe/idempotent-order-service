package com.lilsawe.kit.reconcile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.ToLongFunction;

/**
 * 通用双向对账引擎：把两组记录按业务键比对，输出「一致 / 左侧多 / 右侧多 / 金额不一致」四类结果。
 *
 * <p>不绑定任何领域模型——传两个函数（取键、取金额）即可：
 *
 * <pre>{@code
 * ReconcileReport report = Reconciler.compare(
 *         localOrders,      // 本地订单
 *         channelOrders,    // 渠道结算记录
 *         OrderEntity::getOrderNo,
 *         OrderEntity::getAmountCent);
 * }</pre>
 */
public final class Reconciler {

    private Reconciler() {
    }

    public static <T> ReconcileReport compare(Collection<T> left,
                                              Collection<T> right,
                                              Function<T, String> keyFn,
                                              ToLongFunction<T> amountFn) {
        return compare(left, right, keyFn, amountFn, keyFn, amountFn);
    }

    /**
     * 两侧元素类型不同时的重载：例如左侧是本地订单、右侧是渠道结算记录。
     */
    public static <L, R> ReconcileReport compare(Collection<L> left,
                                                 Collection<R> right,
                                                 Function<L, String> leftKeyFn,
                                                 ToLongFunction<L> leftAmountFn,
                                                 Function<R, String> rightKeyFn,
                                                 ToLongFunction<R> rightAmountFn) {
        Map<String, Long> leftMap = new LinkedHashMap<>();
        if (left != null) {
            for (L item : left) {
                leftMap.put(leftKeyFn.apply(item), leftAmountFn.applyAsLong(item));
            }
        }
        Map<String, Long> rightMap = new LinkedHashMap<>();
        if (right != null) {
            for (R item : right) {
                rightMap.put(rightKeyFn.apply(item), rightAmountFn.applyAsLong(item));
            }
        }
        return compareIndexed(leftMap, rightMap);
    }

    /** 已经索引成 Map（键 -> 金额）时直接比对。 */
    public static ReconcileReport compareIndexed(Map<String, Long> left, Map<String, Long> right) {
        int matched = 0;
        List<ReconcileDiff> diffs = new ArrayList<>();

        for (Map.Entry<String, Long> entry : left.entrySet()) {
            Long rightAmount = right.get(entry.getKey());
            if (rightAmount == null) {
                diffs.add(new ReconcileDiff(entry.getKey(), DiffType.MISSING_IN_RIGHT, entry.getValue(), null));
            } else if (!rightAmount.equals(entry.getValue())) {
                diffs.add(new ReconcileDiff(entry.getKey(), DiffType.AMOUNT_MISMATCH, entry.getValue(), rightAmount));
            } else {
                matched++;
            }
        }
        for (Map.Entry<String, Long> entry : right.entrySet()) {
            if (!left.containsKey(entry.getKey())) {
                diffs.add(new ReconcileDiff(entry.getKey(), DiffType.MISSING_IN_LEFT, null, entry.getValue()));
            }
        }
        return new ReconcileReport(left.size(), right.size(), matched, List.copyOf(diffs));
    }

    private static <T> Map<String, Long> index(Collection<T> items,
                                               Function<T, String> keyFn,
                                               ToLongFunction<T> amountFn) {
        Map<String, Long> map = new HashMap<>();
        if (items == null) {
            return map;
        }
        for (T item : items) {
            map.put(keyFn.apply(item), amountFn.applyAsLong(item));
        }
        return map;
    }
}
