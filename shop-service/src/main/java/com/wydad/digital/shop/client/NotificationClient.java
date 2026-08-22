package com.wydad.digital.shop.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Client best-effort vers notification-service. Une panne de notification
 * ne doit JAMAIS faire échouer un achat de billet déjà enregistré.
 */
@Slf4j
@Component
public class NotificationClient {

    private final RestTemplate restTemplate = new RestTemplate();
    private final String baseUrl;
    private final String internalSecret;

    public NotificationClient(
            @Value("${wydad.notification-service-uri:http://notification-service:8086}") String baseUrl,
            @Value("${wydad.internal-secret:}") String internalSecret) {
        this.baseUrl = baseUrl + "/api/notification";
        this.internalSecret = internalSecret;
    }

    /** Envoie une notification IN_APP ; journalise et ignore toute erreur. */
    public void notifyUser(Long userId, String userEmail, String title, String message, String targetUrl) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (internalSecret != null && !internalSecret.isEmpty()) {
                headers.set("X-Internal-Secret", internalSecret);
            }

            var body = new java.util.HashMap<String, Object>();
            body.put("userId", userId);
            if (userEmail != null) body.put("userEmail", userEmail);
            body.put("title", title);
            body.put("message", message);
            body.put("type", "IN_APP");
            if (targetUrl != null) body.put("targetUrl", targetUrl);

            restTemplate.postForEntity(baseUrl + "/internal/send",
                    new HttpEntity<>(body, headers), String.class);
        } catch (RestClientException e) {
            log.warn("Notification non envoyee a user {}: {}", userId, e.getMessage());
        }
    }
}
