package com.chris64233.commodityoperations.service;

import org.springframework.http.HttpStatus;

/**
 * 可预期的业务规则违反，映射为 422；请求参数不合法映射为 400。
 */
public class BusinessRuleException extends RuntimeException {

    private final HttpStatus status;

    public BusinessRuleException(String message) {
        this(HttpStatus.UNPROCESSABLE_ENTITY, message);
    }

    public BusinessRuleException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
