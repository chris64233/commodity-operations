package com.chris64233.commodityoperations.laytime.domain;

/**
 * 结算单状态。
 */
public enum SettlementStatus {
    /** 草稿（计算产物，可被后续版本取代）。 */
    DRAFT,
    /** 已确认（金额冻结，迟到事件只能触发重算差额调整，不能覆盖）。 */
    CONFIRMED,
    /** 被更新的完整版本取代（仅保留为历史版本）。 */
    SUPERSEDED
}
