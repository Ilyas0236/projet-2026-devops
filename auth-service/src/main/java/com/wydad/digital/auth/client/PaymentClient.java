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
 * Note B.12 (post-correction) : le paiement de l'abonnement est désormais
 * un DÉBIT E-Cash (paiement-service simulé). On ne demande plus de carte
 * bleue à l'utilisateur pour acheter son abonnement saisonnier — le user
 * recharge son wallet E-Cash, puis l'achat débite ce wallet. Le champ
 * cardNumber/expiryDate/cvv/otp reste accepté en DTO pour rétro-compat
 * front (formulaire inchangé) mais est ignoré côté serveur.
 */

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
     * Appelle payment-service /api/payment/internal/debit pour débiter le
     * wallet E-Cash de l'utilisateur. C'est le mode de paiement utilisé pour
     * l'achat d'un abonnement saisonnier (B.12) : on ne demande plus la carte
     * bleue, on débite directement le solde E-Cash du supporter.
     *
     * <p>Endpoint protégé par X-Internal-Secret (la gateway bloque l'accès
     * direct à /api/payment/internal/** depuis l'extérieur). Renvoie
     * 402 Payment Required si le solde est insuffisant — l'exception est
     * propagée telle quelle par {@code RestTemplate} (HttpClientErrorException
     * avec status 402 et le message métier dans le body).
     *
     * @return la référence de la transaction E-Cash (à stocker sur UserSubscription)
     * @throws InsufficientBalanceException si le solde est insuffisant
     */
    public String debitEcash(String email, BigDecimal amount, String reference) {
        String url = paymentServiceUrl + "/api/payment/internal/debit";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (internalSecret != null && !internalSecret.isBlank()) {
            headers.set("X-Internal-Secret", internalSecret);
        }

        Map<String, Object> body = Map.of(
                "email", email,
                "amount", amount,
                "reference", reference
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
            // 402 Payment Required = solde insuffisant. On lève PaymentException
            // avec le message métier tel quel (déjà mappé en 402 par GlobalExceptionHandler).
            if (e.getStatusCode().value() == 402) {
                String msg = e.getResponseBodyAsString();
                log.warn("Solde E-Cash insuffisant pour {} : {} MAD demandés", email, amount);
                throw new PaymentException(
                        msg != null && !msg.isBlank() ? msg : "Solde E-Cash insuffisant");
            }
            log.warn("Débit E-Cash refusé pour {} : {}", email, e.getResponseBodyAsString());
            throw new PaymentException("Débit E-Cash refusé : " + e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("Erreur d'appel payment-service (debit) pour {}", email, e);
            throw new PaymentException("Service de paiement indisponible");
        }
    }

    /**
     * Appelle payment-service /api/payment/card pour débiter le montant.
     * Conservé pour rétro-compat (souscriptions legacy / debug) mais PLUS
     * UTILISÉ par le flow B.12 (remplacé par {@link #debitEcash}).
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
