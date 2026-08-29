package com.wydad.digital.ticket.client;

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

    /**
     * Broadcast IN_APP à tous les utilisateurs actifs du club (fan-out via
     * notification-service). Best-effort : une panne de notification ne doit
     * JAMAIS faire échouer la création d'un événement.
     */
    public void notifyBroadcast(String title, String message, String targetUrl) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (internalSecret != null && !internalSecret.isEmpty()) {
                headers.set("X-Internal-Secret", internalSecret);
            }
            var body = new java.util.HashMap<String, Object>();
            body.put("title", title);
            body.put("message", message);
            body.put("type", "IN_APP");
            if (targetUrl != null) body.put("targetUrl", targetUrl);

            restTemplate.postForEntity(baseUrl + "/internal/broadcast",
                    new HttpEntity<>(body, headers), String.class);
        } catch (RestClientException e) {
            log.warn("Broadcast non envoye: {}", e.getMessage());
        }
    }

    /**
     * Broadcast IN_APP ciblé : fan-out vers une liste explicite d'utilisateurs
     * (IDs). Utilisé pour ne notifier QUE les supporters (USER/ADHERENT) lors
     * d'un nouveau match, sans spammer les admins/présidents/joueurs qui ont
     * leurs propres canaux de notification.
     */
    public void notifyBroadcastTargeted(java.util.List<Long> targetUserIds,
                                        String title, String message, String targetUrl) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (internalSecret != null && !internalSecret.isEmpty()) {
                headers.set("X-Internal-Secret", internalSecret);
            }
            var body = new java.util.HashMap<String, Object>();
            body.put("title", title);
            body.put("message", message);
            body.put("type", "IN_APP");
            if (targetUrl != null) body.put("targetUrl", targetUrl);
            body.put("targetUserIds", targetUserIds);

            restTemplate.postForEntity(baseUrl + "/internal/broadcast-targeted",
                    new HttpEntity<>(body, headers), String.class);
        } catch (RestClientException e) {
            log.warn("Broadcast ciblé non envoye: {}", e.getMessage());
        }
    }
}
