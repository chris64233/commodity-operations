package com.chris64233.commodityoperations.pricing;

import org.springframework.http.HttpStatus;

public class PricingException extends RuntimeException {

    private final HttpStatus status;

    public PricingException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public static PricingException notFound(String message) {
        return new PricingException(HttpStatus.NOT_FOUND, message);
    }

    public static PricingException rejected(String message) {
        return new PricingException(HttpStatus.UNPROCESSABLE_ENTITY, message);
    }

    public static PricingException conflict(String message) {
        return new PricingException(HttpStatus.CONFLICT, message);
    }
}
