package com.lilsawe.kit.reconcile;

/** 单条差异记录。金额单位由调用方约定（建议用最小货币单位的整数）。 */
public record ReconcileDiff(String key, DiffType type, Long leftAmount, Long rightAmount) {
}
