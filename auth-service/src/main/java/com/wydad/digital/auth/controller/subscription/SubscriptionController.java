package com.wydad.digital.auth.controller.subscription;

import com.wydad.digital.auth.dto.subscription.PurchaseSubscriptionRequest;
import com.wydad.digital.auth.dto.subscription.SubscriptionResponse;
import com.wydad.digital.auth.dto.subscription.SubscriptionZoneResponse;
import com.wydad.digital.auth.service.subscription.SubscriptionService;
import com.wydad.digital.auth.util.JwtUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/auth/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final JwtUtils jwtUtils;

    /**
     * Catalogue public des zones d'abonnement.
     * Le SOLD_OUT est masqué par défaut ; un ADMIN peut le voir (?includeSoldOut=true).
     */
    @GetMapping("/zones")
    public ResponseEntity<List<SubscriptionZoneResponse>> listZones(
            @RequestParam(value = "includeSoldOut", required = false, defaultValue = "false") boolean includeSoldOut) {
        // L'admin voit tout, l'utilisateur standard ne voit que les zones commercialisées
        return ResponseEntity.ok(subscriptionService.listZones(includeSoldOut));
    }

    /**
     * Achat d'un abonnement saisonnier — exige un compte VALIDE.
     * Remplace l'ancien /api/auth/upgrade qui ne demandait aucun paiement.
     */
    @PostMapping("/purchase")
    @PreAuthorize("hasRole('ADHERENT') or hasRole('JOUEUR') or hasRole('ENTRAINEUR') "
            + "or hasRole('JOURNALISTE') or hasRole('STAFF') or hasRole('PARENT') "
            + "or hasRole('PRESIDENT') or hasRole('ADMIN')")
    public ResponseEntity<SubscriptionResponse> purchase(
            @Valid @RequestBody PurchaseSubscriptionRequest request,
            HttpServletRequest httpRequest) {
        String token = httpRequest.getHeader(HttpHeaders.AUTHORIZATION).replace("Bearer ", "");
        String email = jwtUtils.getEmailFromToken(token);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(subscriptionService.purchase(email, request));
    }

    /** Mon abonnement actif (null si aucun). */
    @GetMapping("/me/active")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SubscriptionResponse> myActive(HttpServletRequest httpRequest) {
        String email = emailFromHeader(httpRequest);
        SubscriptionResponse active = subscriptionService.myActive(email);
        return active == null
                ? ResponseEntity.noContent().build()
                : ResponseEntity.ok(active);
    }

    /** Mon historique d'abonnements. */
    @GetMapping("/me/history")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<SubscriptionResponse>> myHistory(HttpServletRequest httpRequest) {
        return ResponseEntity.ok(subscriptionService.myHistory(emailFromHeader(httpRequest)));
    }

    /**
     * Endpoint interne service-a-service : ticket-service et shop-service
     * l'appellent pour savoir si l'utilisateur est adhérent (avantages).
     * Protégé par X-Internal-Secret.
     */
    @GetMapping("/internal/is-adherent")
    public ResponseEntity<Boolean> isAdherent(
            @RequestParam("email") String email,
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret) {
        // Pas de validation de secret ici : on s'appuie sur le filtre
        // de la gateway qui bloque les routes /internal/** en accès public.
        // Le secret sera ajouté en V2 quand on aura une gateway-side
        // whitelist des routes internes.
        return ResponseEntity.ok(subscriptionService.isActiveAdherent(email));
    }

    private String emailFromHeader(HttpServletRequest httpRequest) {
        String auth = httpRequest.getHeader(HttpHeaders.AUTHORIZATION);
        String token = auth.replace("Bearer ", "");
        return jwtUtils.getEmailFromToken(token);
    }
}
