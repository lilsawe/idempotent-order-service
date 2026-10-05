package com.lilsawe.order.reconcile;

/** 对账差异类型（订单领域的说法，内部由 kit 的通用 DiffType 映射而来）。 */
public enum DiffKind {
    /** 本地有、渠道无 —— 可能漏推送。 */
    MISSING_IN_CHANNEL,
    /** 渠道有、本地无 —— 可能漏记或重复扣款。 */
    MISSING_IN_LOCAL,
    /** 两侧都有但金额不一致。 */
    AMOUNT_MISMATCH
}
