package com.chris64233.commodityoperations.laytime.domain;

/**
 * 合同停算规则：
 * DEDUCT_PAUSE_INTERVALS —— 暂停区间从装卸时间中扣除（默认口径）；
 * CONTINUOUS —— 合同约定连续计算，任何暂停均不扣除。
 */
public enum StopRule {
    DEDUCT_PAUSE_INTERVALS,
    CONTINUOUS
}
