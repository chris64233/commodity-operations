package com.chris64233.commodityoperations.laytime.web;

/**
 * 可选地指定要确认的版本号；并发重算后原版本不再当前时确认将被拒绝。
 */
public record ConfirmRequest(Integer versionNo) {
}
