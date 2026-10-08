package com.chris64233.commodityoperations.laytime.web;

import com.chris64233.commodityoperations.laytime.exception.EventConflictException;
import com.chris64233.commodityoperations.laytime.exception.LaytimeValidationException;
import com.chris64233.commodityoperations.laytime.exception.NotFoundException;
import com.chris64233.commodityoperations.laytime.exception.SettlementConflictException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;

@RestControllerAdvice(basePackages = "com.chris64233.commodityoperations.laytime")
public class LaytimeExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, Object>> notFound(NotFoundException e) {
        return error(HttpStatus.NOT_FOUND, "NOT_FOUND", e.getMessage());
    }

    @ExceptionHandler(EventConflictException.class)
    public ResponseEntity<Map<String, Object>> conflict(EventConflictException e) {
        return error(HttpStatus.CONFLICT, "EVENT_CONFLICT", e.getMessage());
    }

    @ExceptionHandler(SettlementConflictException.class)
    public ResponseEntity<Map<String, Object>> settlementConflict(SettlementConflictException e) {
        return error(HttpStatus.CONFLICT, "SETTLEMENT_CONFLICT", e.getMessage());
    }

    @ExceptionHandler(LaytimeValidationException.class)
    public ResponseEntity<Map<String, Object>> validation(LaytimeValidationException e) {
        return error(HttpStatus.UNPROCESSABLE_ENTITY, "UNPROCESSABLE_TIMELINE", e.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<Map<String, Object>> badRequest(Exception e) {
        String message = e instanceof MethodArgumentNotValidException ex
                ? ex.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .findFirst().orElse("请求参数不合法")
                : "请求体无法解析";
        return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(Map.of(
                "timestamp", Instant.now().toString(),
                "error", code,
                "message", message));
    }
}
