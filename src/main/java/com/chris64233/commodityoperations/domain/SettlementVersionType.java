package com.chris64233.commodityoperations.domain;

/**
 * PROVISIONAL：合同登记时生成的暂定结算版本。
 * PRICING_ADJUSTMENT：每次点价追加的价差调整版本。
 * FINAL：全部数量点价后生成的唯一最终结算版本。
 */
public enum SettlementVersionType {
    PROVISIONAL,
    PRICING_ADJUSTMENT,
    FINAL
}
