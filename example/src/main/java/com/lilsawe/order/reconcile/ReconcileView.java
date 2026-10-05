package com.lilsawe.order.reconcile;

import java.util.List;

/**
 * 对账结果视图（面向订单领域的字段命名）。
 *
 * <p>kit 里的 {@code ReconcileReport} 是通用结构（left/right），这里映射成业务可读的
 * local/channel，保证对外 API 语义清晰。
 */
public record ReconcileView(int localCount, int channelCount, int matched, List<Diff> diffs) {

    public record Diff(String orderNo, DiffKind type, Long localAmountCent, Long channelAmountCent) {
    }
}
