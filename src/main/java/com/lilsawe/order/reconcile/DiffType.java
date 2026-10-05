package com.lilsawe.order.reconcile;

/** 对账差异类型。 */
public enum DiffType {
    /** 本地有、渠道无：可能漏推送或渠道未结算。 */
    MISSING_IN_CHANNEL,
    /** 渠道有、本地无：可能漏记或重复扣款。 */
    MISSING_IN_LOCAL,
    /** 两侧都有但金额不一致。 */
    AMOUNT_MISMATCH
}
