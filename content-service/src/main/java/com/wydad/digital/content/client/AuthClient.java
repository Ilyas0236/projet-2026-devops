package com.wydad.digital.content.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;

/**
 * Client service-à-service vers auth-service pour récupérer la liste des
 * journalistes actifs — utilisée par la notification « nouvel article publié »
 * (E.2).
 *
 * <p>Best-effort assumé : en cas d'indisponibilité, la liste retournée est
 * vide et l'appelant journalise (pas d'envoi partiel, pas d'exception vers
 * l'appelant).</p>
 */
@Slf4j
@Component
public class AuthClient {

    private final RestTemplate restTemplate = new RestTemplate();
    private final String recipientsUrl;
    private final String internalSecret;

    /** Projection minimale de UserProfileResponse (auth-service). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record JournalistRecipient(
            Long id,
            String email,
            String firstName,
            String lastName,
            String role,
            String statutCompte,
            boolean active) {
    }

    public AuthClient(
            @Value("${wydad.auth-service-uri:http://auth-service:8081}") String baseUrl,
            @Value("${wydad.internal-secret:}") String internalSecret) {
        this.recipientsUrl = baseUrl + "/api/auth/internal/recipients";
        this.internalSecret = internalSecret;
    }

    /**
     * Liste des journalistes éligibles à la notification d'article : rôle
     * JOURNALISTE, compte actif et VALIDÉ. Liste vide si auth-service est
     * injoignable.
     */
    public List<JournalistRecipient> fetchActiveJournalists() {
        HttpHeaders headers = new HttpHeaders();
        if (internalSecret != null && !internalSecret.isEmpty()) {
            headers.set("X-Internal-Secret", internalSecret);
        }

        try {
            ResponseEntity<List<JournalistRecipient>> response = restTemplate.exchange(
                    recipientsUrl + "?roles=JOURNALISTE",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<List<JournalistRecipient>>() {});

            List<JournalistRecipient> journalists = response.getBody() == null
                    ? Collections.emptyList()
                    : response.getBody().stream()
                            .filter(u -> u.active())
                            .filter(u -> "VALIDE".equalsIgnoreCase(u.statutCompte()))
                            .toList();
            log.info("auth-service : {} journaliste(s) actif(s) eligible(s) notif article",
                    journalists.size());
            return journalists;
        } catch (Exception e) {
            log.error("auth-service injoignable ({}) - notification journalistes impossible",
                    recipientsUrl, e);
            return Collections.emptyList();
        }
    }
}
