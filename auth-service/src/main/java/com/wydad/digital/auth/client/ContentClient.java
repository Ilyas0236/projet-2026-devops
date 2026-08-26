package com.wydad.digital.auth.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

/**
 * Client interne vers content-service — §17 : l'accréditation presse est
 * liée à un match RÉEL du calendrier. À l'inscription d'un JOURNALISTE,
 * auth-service vérifie l'existence du match sollicité via l'endpoint
 * interne du content-service (X-Internal-Secret), puis fige un libellé
 * sur le compte (le badge doit rester exact même si le match évolue).
 */
@Slf4j
@Component
public class ContentClient {

    /** Vue minimale du match (MatchResponse du content-service). */
    public record MatchInfo(Long id, String adversaire, String competition, String date) {}

    private final RestTemplate restTemplate = new RestTemplate();
    private final String baseUrl;
    private final String internalSecret;

    public ContentClient(
            @Value("${wydad.content-service-uri:http://content-service:8082}") String baseUrl,
            @Value("${wydad.internal-secret:}") String internalSecret) {
        this.baseUrl = baseUrl + "/api/content/internal";
        this.internalSecret = internalSecret;
    }

    /**
     * Renvoie le libellé lisible du match (« Wydad vs {adversaire} —
     * {compétition}, {date} ») ou null si introuvable / service indisponible.
     * Le null est traité par l'appelant : un match invalide refuse la demande.
     */
    public String fetchMatchLabel(Long matchId) {
        try {
            HttpHeaders headers = new HttpHeaders();
            if (internalSecret != null && !internalSecret.isEmpty()) {
                headers.set("X-Internal-Secret", internalSecret);
            }
            MatchResponseDto dto = restTemplate.exchange(
                    baseUrl + "/matches/" + matchId,
                    HttpMethod.GET, new HttpEntity<>(headers), MatchResponseDto.class).getBody();
            if (dto == null || dto.adversaire() == null) return null;
            String label = "Wydad vs " + dto.adversaire();
            if (dto.competition() != null && !dto.competition().isBlank()) {
                label += " — " + dto.competition();
            }
            if (dto.date() != null) {
                label += ", le " + dto.date();
            }
            return label;
        } catch (HttpClientErrorException.NotFound e) {
            // Match inexistant : demande d'accréditation invalide.
            return null;
        } catch (Exception e) {
            log.warn("Content-service injoignable pour le match {}: {}", matchId, e.getMessage());
            return null;
        }
    }

    /** Désérialisation tolérante de la réponse interne. */
    private record MatchResponseDto(Long id, String adversaire, String competition, String date) {}
}
