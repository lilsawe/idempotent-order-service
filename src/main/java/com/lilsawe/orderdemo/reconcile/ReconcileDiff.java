package com.lilsawe.orderdemo.reconcile;

public record ReconcileDiff(String orderNo, DiffType type, Long localAmountCent, Long channelAmountCent) {
}
