package com.chris64233.commodityoperations.laytime.web;

/**
 * 已确认结算后发起重算时必须给出调整原因，原金额与原因都会保留。
 */
public record RecalculateRequest(String reason) {
}
