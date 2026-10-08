package com.chris64233.commodityoperations.laytime.exception;

/**
 * 时间线无法解释或合同/事件数据不合法时抛出，拒绝结算并指出具体问题。
 */
public class LaytimeValidationException extends RuntimeException {

    public LaytimeValidationException(String message) {
        super(message);
    }
}
