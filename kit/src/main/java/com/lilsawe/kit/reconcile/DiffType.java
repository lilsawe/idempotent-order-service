package com.lilsawe.kit.reconcile;

/** 对账差异类型。 */
public enum DiffType {
    /** 左侧有、右侧无（例如：本地有、渠道无 —— 可能漏推送）。 */
    MISSING_IN_RIGHT,
    /** 右侧有、左侧无（例如：渠道有、本地无 —— 可能漏记或重复扣款）。 */
    MISSING_IN_LEFT,
    /** 两侧都有但金额不一致。 */
    AMOUNT_MISMATCH
}
