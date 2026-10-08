package com.chris64233.commodityoperations.laytime.exception;

/**
 * 同外部事件号但内容（类型或发生时间）与首次登记不一致时抛出。
 */
public class EventConflictException extends RuntimeException {

    public EventConflictException(String message) {
        super(message);
    }
}
