package com.wydad.digital.election.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * B.8 — Client service-à-service vers auth-service pour :
 * <ol>
 *   <li>lister les titulaires actifs (dropdown candidats président côté admin) ;</li>
 *   <li>compter les titulaires ACTIVE à un instant donné (calcul de la
 *       participation X/Y d'un scrutin président au moment de la clôture).</li>
 * </ol>
 *
 * <p>Consomme les endpoints internes introduits au commit 2 (gateway
 * block sur {@code /api/auth/internal/**}) :
 * {@code GET /active-subscribers?season=...} et
 * {@code GET /active-subscribers/count?at=ISO}.</p>
 *
 * <p><b>Best-effort assumé :</b> si auth-service est injoignable,
 * {@link #countActiveAt(LocalDateTime)} renvoie 0 et
 * {@link #listActive(String)} renvoie liste vide. Le scrutin n'est
 * pas bloqué : un échec du snapshot ne doit pas empêcher la
 * publication des résultats. Log d'avertissement à chaque fallback.</p>
 */
@Slf4j
@Component
public class ActiveMembersClient {

    private final RestTemplate restTemplate = new RestTemplate();
    private final String baseUrl;
    private final String internalSecret;

    public ActiveMembersClient(
            @Value("${wydad.auth-service-uri:http://auth-service:8081}") String baseUrl,
            @Value("${wydad.internal-secret:}") String internalSecret) {
        this.baseUrl = baseUrl;
        this.internalSecret = internalSecret;
    }

    /**
     * Liste des titulaires actifs pour la saison donnée (ou toutes
     * saisons si {@code season == null}). Utilisé pour peupler le
     * dropdown candidats président côté UI admin.
     *
     * @return liste d'entrées (id, email, firstName, lastName, season,
     *         validTo, subscriptionId) ; liste vide en cas d'échec.
     */
    public List<Map<String, Object>> listActive(String season) {
        HttpHeaders headers = authHeaders();
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/api/auth/internal/active-subscribers")
                .queryParam("season", season == null ? "" : season)
                .toUriString();
        try {
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {});
            return response.getBody() == null ? List.of() : response.getBody();
        } catch (Exception e) {
            log.warn("auth-service injoignable pour active-subscribers (season={}) : {} — liste vide",
                    season, e.getMessage());
            return List.of();
        }
    }

    /**
     * Compte des titulaires ACTIVE non expirés au moment {@code at}
     * (snapshot). Le paramètre est typiquement le {@code endsAt} de
     * l'élection : on fige le snapshot au moment de la clôture pour
     * qu'un adhérent achetant APRÈS clôture ne soit pas compté à tort
     * comme « n'ayant pas voté ».
     *
     * @return nombre de titulaires uniques ; 0 si auth-service down.
     */
    public long countActiveAt(LocalDateTime at) {
        HttpHeaders headers = authHeaders();
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/api/auth/internal/active-subscribers/count")
                .queryParam("at", at.toString())
                .toUriString();
        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
            Object count = response.getBody() == null ? null : response.getBody().get("count");
            if (count instanceof Number n) return n.longValue();
            return Long.parseLong(String.valueOf(count));
        } catch (Exception e) {
            log.warn("auth-service injoignable pour active-subscribers/count (at={}) : {} — retour 0",
                    at, e.getMessage());
            return 0L;
        }
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        if (internalSecret != null && !internalSecret.isEmpty()) {
            headers.set("X-Internal-Secret", internalSecret);
        }
        return headers;
    }
}
