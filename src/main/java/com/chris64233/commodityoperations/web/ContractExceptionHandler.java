package com.chris64233.commodityoperations.web;

import com.chris64233.commodityoperations.service.ContractException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class ContractExceptionHandler {

    @ExceptionHandler(ContractException.class)
    public ResponseEntity<Map<String, String>> handle(ContractException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(Map.of("error", ex.getMessage()));
    }
}
