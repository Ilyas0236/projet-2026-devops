package com.wydad.digital.auth.dto;

import jakarta.validation.constraints.*;

// S3 : plus de champ membershipLevel ici — le niveau n'est JAMAIS pris du
// client à l'inscription. Le serveur attribue le niveau de départ (ROUGE) ;
// la montée passe par POST /api/auth/upgrade après paiement.
//
// Choix du statut à l'inscription : demandeRole ∈ {JOURNALISTE, JOUEUR,
// ENTRAINEUR, STAFF}. Toute demande crée le compte EN_ATTENTE dans la file
// admin — un client sollicite un statut, il ne l'obtient qu'après validation.
//   - JOUEUR / ENTRAINEUR / STAFF : categorieDemandee obligatoire
//     (U15/U17/U18/U20/SENIOR — SENIOR est la catégorie sénior pro) ;
//   - JOURNALISTE : organismePresse (site/média) + matchSouhaite obligatoires.
public record RegisterRequest(
        @NotBlank @Email String email,
        @NotBlank String phone,
        @NotBlank @Size(min = 6, max = 100) String password,
        @NotBlank String firstName,
        @NotBlank String lastName,
        String referralCode,
        String demandeRole,
        String categorieDemandee,
        String organismePresse,
        String matchSouhaite
) {}
