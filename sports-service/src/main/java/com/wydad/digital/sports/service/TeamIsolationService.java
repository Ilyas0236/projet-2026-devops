package com.wydad.digital.sports.service;

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

    private void requireSameGroup(SportType ownSport, Category ownCategory,
                                  SportType askedSport, Category askedCategory) {
        if (ownSport != askedSport || ownCategory != askedCategory) {
            throw new AccessDeniedException(
                    "Accès limité à votre discipline et votre catégorie");
        }
    }
}
