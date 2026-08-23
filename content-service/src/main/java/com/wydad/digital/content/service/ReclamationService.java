package com.wydad.digital.content.service;

import com.wydad.digital.content.model.Reclamation;
import com.wydad.digital.content.repository.ReclamationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;

/**
 * B.10 — Réclamations & support. Règles serveur :
 * - l'identité du plaignant vient des en-têtes gateway, jamais du body ;
 * - un membre ne voit QUE ses réclamations (filtrage serveur) ;
 * - la réponse est réservée à l'ADMIN et notifie le plaignant (best-effort).
 */
@Slf4j
@Service
public class ReclamationService {

    private final ReclamationRepository repository;
    private final RestTemplate restTemplate = new RestTemplate();
    private final String notificationUri;
    private final String internalSecret;

    public ReclamationService(
            ReclamationRepository repository,
            @org.springframework.beans.factory.annotation.Value(
                    "${wydad.notification-service-uri:http://notification-service:8086}") String notificationUri,
            @org.springframework.beans.factory.annotation.Value("${wydad.internal-secret:}") String internalSecret) {
        this.repository = repository;
        this.notificationUri = notificationUri + "/api/notification/internal/send";
        this.internalSecret = internalSecret;
    }

    /** Création par un membre authentifié — identité imposée côté serveur. */
    @Transactional
    public Reclamation create(Long userId, String userEmail, Reclamation.Subject subject,
                              String title, String description) {
        if (subject == null || title == null || title.isBlank()
                || description == null || description.isBlank()) {
            throw new IllegalArgumentException("Sujet, titre et description sont obligatoires");
        }
        return repository.save(Reclamation.builder()
                .userId(userId)
                .userEmail(userEmail)
                .subject(subject)
                .title(title.trim())
                .description(description.trim())
                .status(Reclamation.Status.OPEN)
                .build());
    }

    /** Un membre ne voit que ses propres réclamations. */
    @Transactional(readOnly = true)
    public List<Reclamation> mine(Long userId) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    /** ADMIN : toutes les réclamations. */
    @Transactional(readOnly = true)
    public List<Reclamation> all() {
        return repository.findAllByOrderByCreatedAtDesc();
    }

    /**
     * ADMIN : réponse officielle + statut. Notifie le plaignant
     * (best-effort : une panne de notification ne fait jamais échouer la réponse).
     */
    @Transactional
    public Reclamation respond(Long id, String response, Reclamation.Status status) {
        if (response == null || response.isBlank()) {
            throw new IllegalArgumentException("La réponse ne peut pas être vide");
        }
        if (status != Reclamation.Status.RESOLVED && status != Reclamation.Status.REJECTED
                && status != Reclamation.Status.IN_PROGRESS && status != Reclamation.Status.OPEN) {
            throw new IllegalArgumentException("Statut invalide");
        }
        Reclamation r = repository.findById(id)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Réclamation introuvable"));
        r.setAdminResponse(response.trim());
        r.setStatus(status);

        notifyClaimant(r);
        return repository.save(r);
    }

    private void notifyClaimant(Reclamation r) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (internalSecret != null && !internalSecret.isEmpty()) {
                headers.set("X-Internal-Secret", internalSecret);
            }
            HashMap<String, Object> body = new HashMap<>();
            body.put("userId", r.getUserId());
            body.put("userEmail", r.getUserEmail());
            body.put("title", "Votre réclamation a été traitée");
            body.put("message", "Réclamation « " + r.getTitle() + " » : " + r.getStatus()
                    + ". Réponse du club : " + r.getAdminResponse());
            body.put("type", "IN_APP");
            body.put("targetUrl", "/profil");

            restTemplate.postForEntity(notificationUri,
                    new HttpEntity<>(body, headers), String.class);
        } catch (RestClientException e) {
            log.warn("Notification non envoyee au plaignant {}: {}", r.getUserId(), e.getMessage());
        }
    }
}
