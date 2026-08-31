package com.wydad.digital.election.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Gestion centralisée des erreurs. Les handlers dédiés AVANT le catch-all :
 * une validation qui échoue doit renvoyer 400 avec le détail, jamais 500.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * B.18 — Code d'erreur renvoyé quand un utilisateur non-adhérent tente
     * de voter à une élection présidentielle. Le front matche ce code
     * pour afficher un message dédié + CTA vers /abonnement.
     */
    public static final String VOTE_REQUIRES_MEMBERSHIP = "VOTE_REQUIRES_MEMBERSHIP";

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    }

    /**
     * B.18 — AVANT le catch-all IllegalStateException : on intercepte
     * spécifiquement le code VOTE_REQUIRES_MEMBERSHIP pour renvoyer 403
     * + un code dédié (au lieu du 409 Conflict générique). Le front
     * détecte ce code et propose un CTA « Acheter ma carte ».
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> conflict(IllegalStateException e) {
        if (VOTE_REQUIRES_MEMBERSHIP.equals(e.getMessage())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("code", VOTE_REQUIRES_MEMBERSHIP,
                            "message", "Pour voter aux élections du président, "
                                    + "vous devez avoir une carte d'abonnement saisonnier active."));
        }
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", e.getMessage()));
    }

    /**
     * Dédié AVANT le catch-all : sinon @PreAuthorize renverrait 500 au lieu
     * de 403 (l'AccessDeniedException serait avalée par le handler générique).
     */
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> accessDenied(
            org.springframework.security.access.AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Accès refusé"));
    }

    @ExceptionHandler(org.springframework.security.authentication.AuthenticationCredentialsNotFoundException.class)
    public ResponseEntity<Map<String, String>> unauthenticated(
            org.springframework.security.authentication.AuthenticationCredentialsNotFoundException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Authentification requise"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> validation(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Requête invalide");
        return ResponseEntity.badRequest().body(Map.of("message", detail));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> unreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(Map.of("message", "Corps de requête illisible ou mal formé"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> generic(Exception e) {
        log.error("Erreur interne", e);
        return ResponseEntity.internalServerError().body(Map.of("message", "Erreur interne"));
    }
}
