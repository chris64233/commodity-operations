package com.chris64233.commodityoperations.laytime.web;

import com.chris64233.commodityoperations.laytime.engine.TimelineException;
import com.chris64233.commodityoperations.laytime.service.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> handleApi(ApiException ex) {
        return ResponseEntity.status(ex.getStatus()).body(Map.of(
                "error", ex.getStatus().getReasonPhrase(),
                "message", ex.getMessage()));
    }

    @ExceptionHandler(TimelineException.class)
    public ResponseEntity<Map<String, Object>> handleTimeline(TimelineException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                "error", "Unprocessable Timeline",
                "message", ex.getMessage(),
                "problems", ex.getProblems()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        return ResponseEntity.badRequest().body(Map.of(
                "error", "Bad Request",
                "message", ex.getBindingResult().getAllErrors().stream()
                        .findFirst().map(e -> e.getDefaultMessage()).orElse("参数校验失败")));
    }
}
