package com.lilsawe.orderdemo.reconcile;

import java.util.List;

public record ReconcileReport(int localCount, int channelCount, int matched, List<ReconcileDiff> diffs) {
}
