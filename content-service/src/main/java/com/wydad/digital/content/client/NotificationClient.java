package com.wydad.digital.content.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * Client best-effort vers notification-service. Une panne de notification ne
 * doit JAMAIS faire échouer la publication d'un article.
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

    /**
     * Broadcast IN_APP ciblé : fan-out vers une liste explicite d'utilisateurs
     * (IDs). Utilisé pour ne notifier QUE les journalistes lors d'un nouvel
     * article (sans spammer les autres rôles).
     */
    public void notifyBroadcastTargeted(List<Long> targetUserIds,
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
            log.warn("Broadcast ciblé (content) non envoye: {}", e.getMessage());
        }
    }
}
