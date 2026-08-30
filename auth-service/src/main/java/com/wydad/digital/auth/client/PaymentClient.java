package com.wydad.digital.auth.client;

import com.wydad.digital.auth.dto.subscription.PurchaseSubscriptionRequest;
import com.wydad.digital.auth.filter.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Client HTTP vers payment-service.
 * Utilisé par SubscriptionService pour valider un paiement carte SIMULÉ
 * avant d'enregistrer l'abonnement.
 *
 * On n'utilise PAS le wallet e-cash interne : l'achat d'un abonnement doit
 * passer par une carte (mockée en démo, vraie passerelle plus tard).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentClient {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${wydad.services.payment-url:http://payment-service:8083}")
    private String paymentServiceUrl;

    @Value("${wydad.internal-secret:}")
    private String internalSecret;

    /**
     * Appelle payment-service /api/payment/card pour débiter le montant.
     * Lève une exception si le paiement échoue (carte refusée, montant
     * incorrect, etc.). Le service appelant fait le rollback.
     *
     * <p>Le contrôleur payment-service /api/payment/card est annoté
     * {@code @PreAuthorize("isAuthenticated()")} et lit l'utilisateur via
     * son {@code UserContextFilter}, qui exige les en-têtes
     * {@code X-User-Email}, {@code X-User-Role} et {@code X-User-Id}
     * (la gateway les injecte à chaque requête entrante). On doit donc
     * les retransmettre tels quels à payment-service — sans quoi
     * l'appel est rejeté en 403 « Utilisateur non authentifié ».
     *
     * @return la référence de la transaction (à stocker sur UserSubscription)
     */
    public String chargeCard(String email, PurchaseSubscriptionRequest request, BigDecimal amount) {
        String url = paymentServiceUrl + "/api/payment/card?email=" + email;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (internalSecret != null && !internalSecret.isBlank()) {
            headers.set("X-Internal-Secret", internalSecret);
        }
        // Propagation du contexte utilisateur (injecté par la gateway).
        String currentEmail = UserContext.getCurrentUserEmail();
        String currentRole = UserContext.getCurrentUserRole();
        Long currentUserId = UserContext.getCurrentUserId();
        if (currentEmail != null) {
            headers.set("X-User-Email", currentEmail);
        }
        if (currentRole != null) {
            headers.set("X-User-Role", currentRole);
        }
        if (currentUserId != null) {
            headers.set("X-User-Id", currentUserId.toString());
        }

        Map<String, Object> body = Map.of(
                "cardNumber", request.cardNumber(),
                "expiryDate", request.expiryDate(),
                "cvv", request.cvv(),
                "otp", request.otp(),
                "amount", amount
        );

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    url, new HttpEntity<>(body, headers), Map.class);
            Object ref = response.getBody() == null ? null : response.getBody().get("reference");
            if (ref == null) {
                throw new PaymentException("payment-service n'a pas renvoyé de référence");
            }
            return ref.toString();
        } catch (HttpClientErrorException e) {
            log.warn("Paiement refusé pour {} : {}", email, e.getResponseBodyAsString());
            throw new PaymentException("Paiement refusé : " + e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("Erreur d'appel payment-service pour {}", email, e);
            throw new PaymentException("Service de paiement indisponible");
        }
    }

    public static class PaymentException extends RuntimeException {
        public PaymentException(String message) {
            super(message);
        }
    }
}
