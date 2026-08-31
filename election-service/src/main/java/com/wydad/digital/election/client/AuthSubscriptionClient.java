package com.wydad.digital.election.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * B.18 — Client service-à-service vers auth-service pour vérifier
 * qu'un électeur a un abonnement saisonnier ACTIF (condition du
 * droit de vote aux élections présidentielles, posée par l'équipe
 * produit : « voter = soutenir le club via la carte d'abonnement »).
 *
 * <p>Consomme {@code GET /api/auth/internal/is-active-subscriber?email=…}
 * — endpoint protégé par la whitelist « X-Internal-Secret » de la
 * gateway (cf. {@code InternalMembershipController} côté auth-service).</p>
 *
 * <p>Best-effort assumé : si auth-service est injoignable, on <b>refuse</b>
 * le vote (retour {@code false}) plutôt que de l'autoriser. Le vote est
 * un acte engageant, on préfère un faux négatif (refus à tort) à un
 * faux positif (vote validé sans vérification).</p>
 */
@Slf4j
@Component
public class AuthSubscriptionClient {

    private final RestTemplate restTemplate = new RestTemplate();
    private final String isActiveSubscriberUrl;
    private final String internalSecret;

    public AuthSubscriptionClient(
            @Value("${wydad.auth-service-uri:http://auth-service:8081}") String baseUrl,
            @Value("${wydad.internal-secret:}") String internalSecret) {
        this.isActiveSubscriberUrl = baseUrl + "/api/auth/internal/is-active-subscriber";
        this.internalSecret = internalSecret;
    }

    /**
     * @param email email de l'électeur (extrait du JWT par UserContext)
     * @return {@code true} si l'utilisateur a un abonnement ACTIF non
     *         expiré, {@code false} sinon (ou si auth-service est
     *         injoignable — refus par défaut, voir note de classe).
     */
    public boolean isActiveSubscriber(String email) {
        if (email == null || email.isBlank()) return false;
        HttpHeaders headers = new HttpHeaders();
        if (internalSecret != null && !internalSecret.isEmpty()) {
            headers.set("X-Internal-Secret", internalSecret);
        }
        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    isActiveSubscriberUrl + "?email=" + email,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    Map.class);
            Map<String, Object> body = response.getBody();
            if (body == null) return false;
            Object active = body.get("active");
            return active instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(active));
        } catch (Exception e) {
            log.warn("auth-service injoignable pour is-active-subscriber({}) : {} — vote refusé par défaut",
                    email, e.getMessage());
            return false;
        }
    }
}
