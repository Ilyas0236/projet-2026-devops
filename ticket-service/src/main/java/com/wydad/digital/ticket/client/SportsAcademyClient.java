package com.wydad.digital.ticket.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * B.18 — Client service-à-service vers sports-service pour le lookup
 * d'un dossier académie (enfant d'un parent). Consommé par le flow
 * « PARENT achète un billet pour son fils » : on doit vérifier que
 * l'enfant est bien rattaché à CE parent (anti-IDOR) avant de créer
 * un User shadow côté auth-service.
 *
 * <p>L'endpoint {@code /api/sports/academy/internal/{id}} est exposé
 * par sports-service (cf. {@code AcademyController#internalLookup}) et
 * protégé par la whitelist « X-Internal-Secret » de la gateway.</p>
 */
@Slf4j
@Component
public class SportsAcademyClient {

    private final RestTemplate restTemplate = new RestTemplate();
    private final String lookupUrlTemplate;
    private final String internalSecret;

    /** Réponse de sports-service. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AcademyMemberView(Long id, Long parentUserId, String childFullName) {}

    public SportsAcademyClient(
            @Value("${wydad.sports-service-uri:http://sports-service:8087}") String baseUrl,
            @Value("${wydad.internal-secret:}") String internalSecret) {
        this.lookupUrlTemplate = baseUrl + "/api/sports/academy/internal/{id}";
        this.internalSecret = internalSecret;
    }

    /**
     * @return les métadonnées d'un dossier académie, ou {@code null} si
     *         introuvable / erreur réseau. L'appelant doit traiter
     *         {@code null} comme une faute fonctionnelle (enfant
     *         inexistant) et lever une exception.
     */
    public AcademyMemberView lookup(Long academyMemberId) {
        HttpHeaders headers = new HttpHeaders();
        if (internalSecret != null && !internalSecret.isEmpty()) {
            headers.set("X-Internal-Secret", internalSecret);
        }
        try {
            ResponseEntity<AcademyMemberView> response = restTemplate.exchange(
                    lookupUrlTemplate,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    AcademyMemberView.class,
                    academyMemberId);
            return response.getBody();
        } catch (Exception e) {
            log.error("sports-service injoignable pour lookup academy {} : {}",
                    academyMemberId, e.getMessage());
            return null;
        }
    }
}
