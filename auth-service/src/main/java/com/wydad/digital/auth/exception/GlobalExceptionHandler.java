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

    /**
     * Règle « un seul abonnement par saison » : l'utilisateur a déjà un
     * abonnement ACTIVE ou REPLACED pour la saison courante, on refuse
     * l'achat, l'upgrade ou le ré-achat. — 409 ALREADY_SUBSCRIBED.
     *
     * <p>Code retour 409 Conflict plutôt que 400 : c'est un état de
     * ressource, pas une faute de saisie. Le message d'erreur est
     * directement affichable côté front (le dialog de paiement
     * abonnement.component l'affiche tel quel).</p>
     */
    @ExceptionHandler(com.wydad.digital.auth.service.subscription.SubscriptionService.AlreadySubscribedException.class)
    public ResponseEntity<ErrorResponse> handleAlreadySubscribed(
            com.wydad.digital.auth.service.subscription.SubscriptionService.AlreadySubscribedException ex,
            HttpServletRequest request) {

        ErrorResponse error = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.CONFLICT.value(),
                "ALREADY_SUBSCRIBED",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    // ──────────────────── B.17 — Workflow accréditation presse ────────────────────

    /**
     * B.17 — Le journaliste n'a pas de photo de profil alors qu'il tente
     * de créer une demande d'accréditation. 400 dédié (et non 400
     * générique) pour que le front puisse afficher un message précis
     * (« Téléversez votre photo avant de demander une accréditation »).
     */
    @ExceptionHandler(com.wydad.digital.auth.service.press.PressAccreditationService.PhotoRequiredException.class)
    public ResponseEntity<ErrorResponse> handlePhotoRequired(
            com.wydad.digital.auth.service.press.PressAccreditationService.PhotoRequiredException ex,
            HttpServletRequest request) {

        ErrorResponse error = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                "PHOTO_REQUIRED",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * B.17 — Le journaliste a déjà une demande (en cours, validée ou
     * refusée) pour le couple (user, matchId). 409 Conflict (état de
     * ressource, pas faute de saisie). Le bouton « Demander une
     * accréditation » doit être désactivé côté front si une demande
     * existe déjà pour ce match.
     */
    @ExceptionHandler(com.wydad.digital.auth.service.press.PressAccreditationService.DuplicateAccreditationException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateAccreditation(
            com.wydad.digital.auth.service.press.PressAccreditationService.DuplicateAccreditationException ex,
            HttpServletRequest request) {

        ErrorResponse error = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.CONFLICT.value(),
                "DUPLICATE_ACCREDITATION",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    /**
     * B.17 — Aucune demande d'accréditation ne correspond à l'id
     * fourni. Couvre aussi la garde « un journaliste ne peut pas
     * voir/télécharger la demande d'un autre » : on renvoie
     * intentionnellement 404 plutôt que 403 pour ne pas révéler
     * l'existence d'une demande.
     */
    @ExceptionHandler(com.wydad.digital.auth.service.press.PressAccreditationService.AccreditationNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAccreditationNotFound(
            com.wydad.digital.auth.service.press.PressAccreditationService.AccreditationNotFoundException ex,
            HttpServletRequest request) {

        ErrorResponse error = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.NOT_FOUND.value(),
                "ACCREDITATION_NOT_FOUND",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    /**
     * B.17 — Le matchId fourni ne correspond à aucun match du calendrier
     * content-service. 400 (faute de saisie : le journaliste a copié un
     * id inexistant, ou l'admin a supprimé le match depuis).
     */
    @ExceptionHandler(com.wydad.digital.auth.service.press.PressAccreditationService.MatchNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleMatchNotFound(
            com.wydad.digital.auth.service.press.PressAccreditationService.MatchNotFoundException ex,
            HttpServletRequest request) {

        ErrorResponse error = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                "MATCH_NOT_FOUND",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
}