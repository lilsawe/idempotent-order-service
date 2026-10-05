package com.lilsawe.order.reconcile;

public record ReconcileDiff(String orderNo, DiffType type, Long localAmountCent, Long channelAmountCent) {
}
