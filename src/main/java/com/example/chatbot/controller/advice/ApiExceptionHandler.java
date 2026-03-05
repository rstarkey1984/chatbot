package com.example.chatbot.controller.advice;

import com.example.chatbot.common.exception.RateLimitExceededException;
import com.example.chatbot.controller.AppController;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice(assignableTypes = AppController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> {
                    String defaultMessage = fe.getDefaultMessage();
                    if (defaultMessage != null && !defaultMessage.isBlank()) {
                        return defaultMessage;
                    }
                    return fe.getField() + " 값이 올바르지 않습니다.";
                })
                .orElse("요청값이 올바르지 않습니다.");

        return badRequest(msg);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException e) {
        return badRequest(e.getMessage());
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<Map<String, Object>> handleRateLimit(RateLimitExceededException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(Map.of(
                        "status", HttpStatus.TOO_MANY_REQUESTS.value(),
                        "error", HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(),
                        "message", e.getMessage() == null ? "요청이 너무 많습니다." : e.getMessage()
                ));
    }

    private ResponseEntity<Map<String, Object>> badRequest(String message) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                        "status", HttpStatus.BAD_REQUEST.value(),
                        "error", HttpStatus.BAD_REQUEST.getReasonPhrase(),
                        "message", message == null ? "요청값이 올바르지 않습니다." : message
                ));
    }
}
