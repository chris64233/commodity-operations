package com.chris64233.commodityoperations.domain;

/**
 * 合同生命周期状态。
 * OPEN：存在未点价数量，可继续点价。
 * FULLY_PRICED：全部数量已点价但尚未生成最终结算。
 * FINALLY_SETTLED：最终结算已生成，拒绝任何迟到点价。
 */
public enum ContractStatus {
    OPEN,
    FULLY_PRICED,
    FINALLY_SETTLED
}
