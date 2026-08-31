package com.wydad.digital.auth.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Client interne vers sports-service — à la validation d'un compte sportif
 * (JOUEUR, ENTRAINEUR, STAFF) par l'ADMIN, l'auth-service crée la fiche
 * roster correspondante (players / staff) via l'endpoint interne du
 * sports-service (X-Internal-Secret). Sans cette fiche, l'espace joueur ou
 * staff ne trouve rien : « Impossible de charger votre espace ».
 *
 * <p>Best-effort : un échec n'invalide jamais la décision admin — la fiche
 * reste créable manuellement depuis le back-office.</p>
 */
@Slf4j
@Component
public class SportsClient {

    private final RestTemplate restTemplate = new RestTemplate();
    private final String baseUrl;
    private final String internalSecret;

    public SportsClient(
            @Value("${wydad.sports-service-uri:http://sports-service:8087}") String baseUrl,
            @Value("${wydad.internal-secret:}") String internalSecret) {
        this.baseUrl = baseUrl + "/api/sports/internal/roster";
        this.internalSecret = internalSecret;
    }

    /**
     * Crée (ou met à jour) la fiche roster d'un compte sportif validé.
     * roleDemande : JOUEUR | ENTRAINEUR | STAFF ; discipline/catégorie :
     * celles sollicitées à l'inscription (déjà validées côté inscription).
     * @return true si la fiche est en place.
     */
    public boolean createRosterEntry(Long userId, String fullName,
                                     String roleDemande, String discipline, String categorie) {
        if (discipline == null || categorie == null) {
            log.warn("Fiche roster non créée pour user {} : discipline/catégorie absentes", userId);
            return false;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            if (internalSecret != null && !internalSecret.isEmpty()) {
                headers.set("X-Internal-Secret", internalSecret);
            }
            // Sérialisation JSON explicite : Map.of ne tolère aucune valeur
            // null (NPE immédiate) et le convertisseur par défaut de
            // RestTemplate a déjà produit un corps dégradé en prod.
            String safeName = fullName.replace("\"", "").trim();
            boolean isPlayer = "JOUEUR".equals(roleDemande);
            String json = isPlayer
                    ? String.format(
                        "{\"userId\":%d,\"fullName\":\"%s\",\"sportType\":\"%s\",\"category\":\"%s\"}",
                        userId, safeName, discipline.trim(), categorie.trim())
                    // ENTRAINEUR / STAFF : fiche staff — rôle par défaut MANAGER,
                    // précisable ensuite par l'ADMIN depuis le back-office.
                    : String.format(
                        "{\"userId\":%d,\"fullName\":\"%s\",\"role\":\"MANAGER\",\"sportType\":\"%s\",\"assignedCategory\":\"%s\"}",
                        userId, safeName, discipline.trim(), categorie.trim());
            restTemplate.exchange(
                    baseUrl + (isPlayer ? "/players" : "/staff"),
                    HttpMethod.POST, new HttpEntity<>(json, headers), Void.class);
            log.info("Fiche roster créée : user {} -> {} {} {}", userId, roleDemande, discipline, categorie);
            return true;
        } catch (Exception e) {
            log.error("Création fiche roster échouée pour user {} ({}, {}) : {}",
                    userId, discipline, categorie, e.getMessage());
            return false;
        }
    }

    /**
     * Supprime la fiche roster (players + staff) d'un compte supprimé côté
     * auth-service. Best-effort : un échec est logué, l'auth-service ne
     * ré-essaie pas (à nettoyer via un script SQL si besoin).
     */
    public boolean deleteRosterEntry(Long userId) {
        try {
            HttpHeaders headers = new HttpHeaders();
            if (internalSecret != null && !internalSecret.isEmpty()) {
                headers.set("X-Internal-Secret", internalSecret);
            }
            // L'endpoint interne accepte les deux suffixes (players + staff)
            // — l'un des deux renverra 404 si la fiche n'existe pas, on
            // tolère. Pour une suppression effective, on tente les deux.
            restTemplate.exchange(
                    baseUrl + "/players/user/" + userId,
                    HttpMethod.DELETE, new HttpEntity<>(headers), Void.class);
            restTemplate.exchange(
                    baseUrl + "/staff/user/" + userId,
                    HttpMethod.DELETE, new HttpEntity<>(headers), Void.class);
            log.info("Fiche roster supprimée pour user {}", userId);
            return true;
        } catch (Exception e) {
            log.error("Suppression fiche roster échouée pour user {} : {}", userId, e.getMessage());
            return false;
        }
    }

    /**
     * B.18 — Lookup d'un AcademyMember (enfant d'un parent) par son id.
     * Utilisé par le flow « PARENT achète pour son fils » : on doit savoir
     * à quel parent l'enfant est rattaché (sécurité IDOR) et comment il
     * s'appelle (pour la carte et le PDF).
     *
     * <p>Retourne {@code null} si l'enfant n'existe pas ou si l'appel
     * échoue (best-effort, comme {@link #createRosterEntry}). L'appelant
     * doit traiter {@code null} comme une erreur fonctionnelle.</p>
     */
    @SuppressWarnings("unchecked")
    public java.util.Map<String, Object> getAcademyMember(Long academyMemberId) {
        try {
            HttpHeaders headers = new HttpHeaders();
            if (internalSecret != null && !internalSecret.isEmpty()) {
                headers.set("X-Internal-Secret", internalSecret);
            }
            String url = baseUrl.replace("/internal/roster", "/academy/internal/" + academyMemberId);
            org.springframework.http.ResponseEntity<java.util.Map> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), java.util.Map.class);
            if (response.getBody() == null) return null;
            return (java.util.Map<String, Object>) response.getBody();
        } catch (Exception e) {
            log.error("Lookup AcademyMember {} échoué : {}", academyMemberId, e.getMessage());
            return null;
        }
    }
}
