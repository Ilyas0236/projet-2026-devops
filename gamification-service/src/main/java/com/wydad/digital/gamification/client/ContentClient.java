package com.wydad.digital.gamification.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Client service-à-service vers content-service pour la validation des
 * pronostics : un matchId soumis doit correspondre à un match réel,
 * à venir (PROGRAMME), dont la date/heure de début n'est pas dépassée.
 */
@Slf4j
@Component
public class ContentClient {

    /** Projection minimale du match content-service. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MatchInfo(
            Long id,
            LocalDate date,
            LocalTime heure,
            String adversaire,
            String competition,
            String lieu,
            Integer scoreWydad,
            Integer scoreAdversaire,
            String statut,
            String sport) {
    }

    private final RestTemplate restTemplate = new RestTemplate();
    private final String matchUrlTemplate;
    private final String internalSecret;

    public ContentClient(
            @Value("${wydad.content-service-uri:http://content-service:8082}") String baseUrl,
            @Value("${wydad.internal-secret:}") String internalSecret) {
        this.matchUrlTemplate = baseUrl + "/api/content/internal/matches/{id}";
        this.internalSecret = internalSecret;
    }

    /**
     * Renvoie le match s'il existe et est encore pronostiquable
     * (statut PROGRAMME et coup d'envoi non passé). Lève une
     * IllegalArgumentException sinon.
     */
    public MatchInfo getPredictableMatch(Long matchId) {
        HttpHeaders headers = new HttpHeaders();
        if (internalSecret != null && !internalSecret.isEmpty()) {
            headers.set("X-Internal-Secret", internalSecret);
        }

        try {
            ResponseEntity<MatchInfo> response = restTemplate.exchange(
                    matchUrlTemplate, HttpMethod.GET, new HttpEntity<>(headers), MatchInfo.class, matchId);
            MatchInfo match = response.getBody();
            if (match == null) {
                throw new IllegalArgumentException("Match introuvable");
            }
            if (!"PROGRAMME".equals(match.statut())) {
                throw new IllegalArgumentException(
                        "Les pronostics sont fermés pour ce match (statut : " + match.statut() + ")");
            }
            java.time.LocalDateTime kickoff = java.time.LocalDateTime.of(match.date(), match.heure());
            if (java.time.LocalDateTime.now().isAfter(kickoff)) {
                throw new IllegalArgumentException("Les pronostics sont fermés : le match a commencé");
            }
            return match;
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode().value() == 404) {
                throw new IllegalArgumentException("Match introuvable");
            }
            log.warn("content-service a repondu {} pour le match {}", e.getStatusCode(), matchId);
            throw new IllegalStateException("Validation du match impossible, réessayez plus tard", e);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            // Fail-fast : sans validation on accepterait des pronostics sur des
            // matches inconnus ou déjà joues -> farm de points trivial.
            log.error("content-service injoignable pour le match {}", matchId, e);
            throw new IllegalStateException("Validation du match impossible, réessayez plus tard", e);
        }
    }
}
