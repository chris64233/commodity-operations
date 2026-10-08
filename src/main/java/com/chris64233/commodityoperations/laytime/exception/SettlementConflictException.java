package com.chris64233.commodityoperations.laytime.exception;

/**
 * 结算状态或版本冲突（确认不存在/非当前草稿、非最新事件版本的草稿不能成为当前版本等）。
 */
public class SettlementConflictException extends RuntimeException {

    public SettlementConflictException(String message) {
        super(message);
    }
}
