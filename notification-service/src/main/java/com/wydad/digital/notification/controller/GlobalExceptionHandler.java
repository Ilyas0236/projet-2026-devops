package com.wydad.digital.notification.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Gestion homogène des erreurs du notification-service (format
 * {error, message, timestamp} identique aux autres services).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(org.springframework.security.access.AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "error", "FORBIDDEN",
                "message", "Acces refuse",
                "timestamp", LocalDateTime.now().toString()
        ));
    }

    @ExceptionHandler(jakarta.persistence.EntityNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(jakarta.persistence.EntityNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "error", "NOT_FOUND",
                "message", ex.getMessage(),
                "timestamp", LocalDateTime.now().toString()
        ));
    }

    /** Validation jakarta (@Valid/@NotBlank...) : 400 avec le message du champ. */
    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(org.springframework.web.bind.MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(f -> f.getField() + " : " + f.getDefaultMessage())
                .orElse("Requête invalide");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "error", "BAD_REQUEST",
                "message", message,
                "timestamp", LocalDateTime.now().toString()
        ));
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<Map<String, Object>> handleBadRequest(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "error", "BAD_REQUEST",
                "message", ex.getMessage(),
                "timestamp", LocalDateTime.now().toString()
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "error", "INTERNAL_SERVER_ERROR",
                "message", ex.getMessage() != null ? ex.getMessage() : "Erreur interne",
                "timestamp", LocalDateTime.now().toString()
        ));
    }
}
