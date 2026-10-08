package com.chris64233.commodityoperations.service;

import org.springframework.http.HttpStatus;

/**
 * 同一外部点价号以不同内容重复提交时抛出，映射为 409。
 */
public class ConflictException extends BusinessRuleException {

    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
