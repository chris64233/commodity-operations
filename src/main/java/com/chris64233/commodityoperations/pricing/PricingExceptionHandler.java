package com.chris64233.commodityoperations.pricing;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class PricingExceptionHandler {

    @ExceptionHandler(PricingException.class)
    public ResponseEntity<Map<String, Object>> handle(PricingException ex) {
        return ResponseEntity.status(ex.getStatus()).body(Map.of(
                "timestamp", Instant.now().toString(),
                "status", ex.getStatus().value(),
                "error", ex.getMessage()));
    }
}
