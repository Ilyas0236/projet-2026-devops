package com.wydad.digital.auth.dto;

import com.wydad.digital.auth.model.MembershipLevel;
import com.wydad.digital.auth.model.Role;
import com.wydad.digital.auth.model.StatutCompte;

import java.time.LocalDateTime;

public record UserProfileResponse(
        Long id,
        String email,
        String phone,
        String firstName,
        String lastName,
        MembershipLevel membershipLevel,
        Role role,
        StatutCompte statutCompte,
        LocalDateTime membershipExpiresAt,
        String referralCode,
        boolean active,
        boolean kycVerified,
        LocalDateTime createdAt,
        // Demande d'inscription multi-statuts : catégorie sportive sollicitée
        // (JOUEUR/ENTRAINEUR/STAFF), organe de presse + match (JOURNALISTE),
        // et motif de refus éventuel — affichés dans l'écran admin des demandes.
        String categorieDemandee,
        String organismePresse,
        String matchSouhaite,
        String motifRefus
) {}