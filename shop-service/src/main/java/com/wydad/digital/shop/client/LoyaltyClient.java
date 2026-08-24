package com.wydad.digital.shop.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

/**
 * Fidélité — client best-effort vers gamification-service : crédite des
 * points au membre après une commande payée. Une panne de gamification ne
 * doit JAMAIS faire échouer un achat déjà débité (même logique que la
 * notification).
 */
@Slf4j
@Component
public class LoyaltyClient {

    private final RestTemplate restTemplate = new RestTemplate();
    private final String creditUrl;
    private final String internalSecret;

    public LoyaltyClient(
            @Value("${wydad.gamification-service-uri:http://gamification-service:8088}") String baseUrl,
            @Value("${wydad.internal-secret:}") String internalSecret) {
        this.creditUrl = baseUrl + "/api/gamification/internal/points/credit";
        this.internalSecret = internalSecret;
    }

    /** Crédite des points proportionnels au montant ; journalise et ignore toute erreur. */
    public void creditPointsForPurchase(Long userId, BigDecimal amountDh, String reference) {
        if (userId == null || amountDh == null || amountDh.signum() <= 0) {
            return;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (internalSecret != null && !internalSecret.isEmpty()) {
                headers.set("X-Internal-Secret", internalSecret);
            }

            var body = new java.util.HashMap<String, Object>();
            body.put("userId", userId);
            body.put("amountDh", amountDh);

            restTemplate.postForEntity(creditUrl, new HttpEntity<>(body, headers), String.class);
            log.info("Points fidelite credites pour {} (ref {})", userId, reference);
        } catch (RestClientException e) {
            log.warn("Points fidelite non credites pour user {}: {}", userId, e.getMessage());
        }
    }
}
