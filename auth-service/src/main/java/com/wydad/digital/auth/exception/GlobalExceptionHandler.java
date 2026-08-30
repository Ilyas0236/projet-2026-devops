package com.wydad.digital.auth.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.LocalDateTime;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    /**
     * AccessDeniedException (levee par @PreAuthorize) doit donner 403 et non
     * tomber dans le handler generique qui renvoie 500.
     */
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            org.springframework.security.access.AccessDeniedException ex, HttpServletRequest request) {

        ErrorResponse error = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.FORBIDDEN.value(),
                "FORBIDDEN",
                "Accès refusé",
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    /**
     * Phase 0 : credentials correctes mais compte EN_ATTENTE/REFUSE → 403
     * avec le message explicite (différent du 401 "identifiants invalides").
     */
    @ExceptionHandler(CompteNonValideException.class)
    public ResponseEntity<ErrorResponse> handleCompteNonValide(
            CompteNonValideException ex, HttpServletRequest request) {

        ErrorResponse error = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.FORBIDDEN.value(),
                "COMPTE_NON_VALIDE",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleEmailAlreadyExists(
            EmailAlreadyExistsException ex, HttpServletRequest request) {

        ErrorResponse error = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                "BAD_REQUEST",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(
            InvalidCredentialsException ex, HttpServletRequest request) {

        ErrorResponse error = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.UNAUTHORIZED.value(),
                "UNAUTHORIZED",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFound(
            UserNotFoundException ex, HttpServletRequest request) {

        ErrorResponse error = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.NOT_FOUND.value(),
                "NOT_FOUND",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    /**
     * Phase 1 ter — panne du service d'upload (Cloudinary) : 503 avec un
     * message actionnable, distinct d'une faute de saisie (400). Le détail
     * technique est journalisé côté serveur uniquement.
     */
    @ExceptionHandler(CloudinaryIndisponibleException.class)
    public ResponseEntity<ErrorResponse> handleCloudinaryIndisponible(
            CloudinaryIndisponibleException ex, HttpServletRequest request) {

        log.warn("Upload Cloudinary indisponible sur {}: {}", request.getRequestURI(), ex.getDetail());
        ErrorResponse error = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                "SERVICE_UNAVAILABLE",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error);
    }

    /**
     * Violation de contrainte sur un paramètre (@Validated + @NotBlank sur le
     * motif de refus, etc.) : 400 avec la violation lisible — sinon tomberait
     * dans le handler générique RuntimeException.
     */
    @ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            jakarta.validation.ConstraintViolationException ex, HttpServletRequest request) {

        String details = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .collect(java.util.stream.Collectors.joining("; "));
        ErrorResponse error = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                "BAD_REQUEST",
                details,
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * §24 : appel direct hors gateway (headers X-User-* absents) → 401,
     * distinct du 400 générique pour rester diagnostiquable.
     */
    @ExceptionHandler(GatewayIdentityMissingException.class)
    public ResponseEntity<ErrorResponse> handleGatewayIdentityMissing(
            GatewayIdentityMissingException ex, HttpServletRequest request) {

        log.warn("Appel hors passerelle rejeté sur {}", request.getRequestURI());
        ErrorResponse error = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.UNAUTHORIZED.value(),
                "UNAUTHORIZED",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntime(
            RuntimeException ex, HttpServletRequest request) {

        ErrorResponse error = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                "BAD_REQUEST",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * B.12 — paiement refusé par payment-service : on propage 402 Payment
     * Required (code HTTP non standard mais clair) plutôt que 400.
     */
    @ExceptionHandler(com.wydad.digital.auth.client.PaymentClient.PaymentException.class)
    public ResponseEntity<ErrorResponse> handlePaymentFailed(
            com.wydad.digital.auth.client.PaymentClient.PaymentException ex,
            HttpServletRequest request) {

        log.warn("Paiement refusé sur {}: {}", request.getRequestURI(), ex.getMessage());
        ErrorResponse error = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.PAYMENT_REQUIRED.value(),
                "PAYMENT_REQUIRED",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(error);
    }

    /**
     * Plan d'abonnement introuvable (id ou code) — 404.
     * Couvre SubscriptionService.PlanNotFoundException et
     * SubscriptionPlanService.PlanNotFoundException.
     */
    @ExceptionHandler({
            com.wydad.digital.auth.service.subscription.SubscriptionService.PlanNotFoundException.class,
            com.wydad.digital.auth.service.subscription.SubscriptionPlanService.PlanNotFoundException.class
    })
    public ResponseEntity<ErrorResponse> handlePlanNotFound(
            RuntimeException ex, HttpServletRequest request) {

        ErrorResponse error = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.NOT_FOUND.value(),
                "PLAN_NOT_FOUND",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    /**
     * Plan désactivé (isActive=false) ou plan référencé par des abonnements
     * existants (delete impossible) — 409.
     */
    @ExceptionHandler({
            com.wydad.digital.auth.service.subscription.SubscriptionService.PlanNotActiveException.class,
            com.wydad.digital.auth.service.subscription.SubscriptionPlanService.PlanInUseException.class
    })
    public ResponseEntity<ErrorResponse> handlePlanConflict(
            RuntimeException ex, HttpServletRequest request) {

        ErrorResponse error = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.CONFLICT.value(),
                "PLAN_CONFLICT",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    /**
     * Code de plan déjà utilisé (UNIQUE) — 409.
     */
    @ExceptionHandler(com.wydad.digital.auth.service.subscription.SubscriptionPlanService.DuplicatePlanCodeException.class)
    public ResponseEntity<ErrorResponse> handleDuplicatePlanCode(
            com.wydad.digital.auth.service.subscription.SubscriptionPlanService.DuplicatePlanCodeException ex,
            HttpServletRequest request) {

        ErrorResponse error = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.CONFLICT.value(),
                "DUPLICATE_PLAN_CODE",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }
}