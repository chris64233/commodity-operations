package com.chris64233.commodityoperations.laytime.domain;

/**
 * 结算单版本状态。CONFIRMED 版本金额不可变，迟到事件只能产生新的调整版本。
 */
public enum SettlementStatus {
    DRAFT,
    CONFIRMED
}
