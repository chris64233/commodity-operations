package com.chris64233.commodityoperations.service;

import org.springframework.http.HttpStatus;

public class ContractException extends RuntimeException {

    private final HttpStatus status;

    public ContractException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public static ContractException notFound(String message) {
        return new ContractException(HttpStatus.NOT_FOUND, message);
    }

    public static ContractException conflict(String message) {
        return new ContractException(HttpStatus.CONFLICT, message);
    }

    public static ContractException unprocessable(String message) {
        return new ContractException(HttpStatus.UNPROCESSABLE_ENTITY, message);
    }
}
