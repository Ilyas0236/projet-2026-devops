package com.wydad.digital.sports.service;

import com.wydad.digital.sports.client.AuthUserInfoClient;
import com.wydad.digital.sports.enums.Category;
import com.wydad.digital.sports.enums.SportType;
import com.wydad.digital.sports.filter.SportsUserContext;
import com.wydad.digital.sports.model.Player;
import com.wydad.digital.sports.model.Staff;
import com.wydad.digital.sports.repository.PlayerRepository;
import com.wydad.digital.sports.repository.StaffRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/**
 * Isolation discipline + catégorie (cahier des charges §6/§24).
 *
 * Règle centrale : le couple discipline+catégorie autorisé n'est JAMAIS lu
 * depuis les paramètres de la requête (falsifiables) mais résolu côté serveur
 * depuis le profil Staff ou Player du compte appelant (rattaché par userId JWT).
 *
 *  - ADMIN / PRESIDENT : vision globale, peuvent interroger chaque groupe ;
 *  - ENTRAINEUR / STAFF : uniquement leur propre équipe (ex. Football U17) ;
 *  - JOUEUR : uniquement son propre groupe.
 *
 * Toute tentative hors groupe → 403 (AccessDeniedException).
 */
@Service
@RequiredArgsConstructor
public class TeamIsolationService {

    private final StaffRepository staffRepository;
    private final PlayerRepository playerRepository;
    private final AuthUserInfoClient authUserInfoClient;

    /**
     * Vérifie que l'appelant a le droit de consulter le groupe
     * sportType+category demandé. Lève AccessDeniedException sinon.
     */
    public void ensureCanQueryTeam(SportType sportType, Category category) {
        String role = SportsUserContext.getCurrentUserRole();

        // Vision globale : back-office admin et espace président.
        if ("ADMIN".equals(role) || "PRESIDENT".equals(role)) {
            return;
        }

        if ("ENTRAINEUR".equals(role) || "STAFF".equals(role)) {
            Staff staff = staffRepository.findByUserId(SportsUserContext.getCurrentUserId())
                    .orElseThrow(() -> new AccessDeniedException(
                            "Aucun profil encadrement rattaché à ce compte"));
            requireSameGroup(staff.getSportType(), staff.getAssignedCategory(), sportType, category);
            return;
        }

        if ("JOUEUR".equals(role)) {
            Player player = playerRepository.findByUserId(SportsUserContext.getCurrentUserId())
                    .orElseThrow(() -> new AccessDeniedException(
                            "Aucune fiche joueur rattachée à ce compte"));
            requireSameGroup(player.getSportType(), player.getCategory(), sportType, category);
            return;
        }

        // Tout autre rôle (VISITEUR, ADHERENT, PARENT...) : pas d'accès aux effectifs.
        throw new AccessDeniedException("Accès aux effectifs non autorisé pour ce rôle");
    }

    /**
     * C.21 — Vérifie que l'appelant peut consulter toute une discipline
     * (toutes catégories confondues). ADMIN a la vision globale, PRESIDENT
     * ne peut consulter QUE sa propre discipline (vérifiée via auth-service
     * — disciplineDemandee du profil). Tout autre rôle → 403.
     */
    public void ensureCanQueryDiscipline(SportType sportType) {
        String role = SportsUserContext.getCurrentUserRole();

        if ("ADMIN".equals(role)) {
            return;
        }

        if ("PRESIDENT".equals(role)) {
            // Le président ne peut consulter QUE sa discipline. disciplineDemandee
            // est posée à l'inscription et stockée sur le profil serveur, pas
            // fournie par le client. On la lit via auth-service (gateway
            // X-Internal-Secret) — pas falsifiable.
            Long meId = SportsUserContext.getCurrentUserId();
            if (meId == null) {
                throw new AccessDeniedException("Président non identifié");
            }
            String ownDiscipline = authUserInfoClient.getDisciplineByUserId(meId);
            if (ownDiscipline == null || ownDiscipline.isBlank()) {
                throw new AccessDeniedException(
                        "Aucun président sans discipline — valider la candidature admin avant");
            }
            if (!ownDiscipline.equalsIgnoreCase(sportType.name())) {
                throw new AccessDeniedException(
                        "Un président ne peut consulter que sa propre discipline ("
                                + ownDiscipline + ")");
            }
            return;
        }

        throw new AccessDeniedException(
                "Accès global à une discipline non autorisé pour ce rôle");
    }

    private void requireSameGroup(SportType ownSport, Category ownCategory,
                                  SportType askedSport, Category askedCategory) {
        if (ownSport != askedSport || ownCategory != askedCategory) {
            throw new AccessDeniedException(
                    "Accès limité à votre discipline et votre catégorie");
        }
    }
}
