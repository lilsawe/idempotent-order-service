package com.lilsawe.kit.reconcile;

import java.util.List;

/** 对账结果：两侧总量、一致数量与全部差异明细。 */
public record ReconcileReport(int leftCount, int rightCount, int matched, List<ReconcileDiff> diffs) {

    public boolean balanced() {
        return diffs.isEmpty();
    }
}
