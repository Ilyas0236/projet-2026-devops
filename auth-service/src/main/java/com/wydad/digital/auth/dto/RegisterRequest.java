package com.wydad.digital.auth.dto;

import jakarta.validation.constraints.*;

// S3 : plus de champ membershipLevel ici — le niveau n'est JAMAIS pris du
// client à l'inscription. Le serveur attribue le niveau de départ (ROUGE) ;
// la montée passe par POST /api/auth/upgrade après paiement.
//
// Choix du statut à l'inscription : demandeRole ∈ {JOURNALISTE, JOUEUR,
// ENTRAINEUR, STAFF}. Toute demande crée le compte EN_ATTENTE dans la file
// admin — un client sollicite un statut, il ne l'obtient qu'après validation.
//   - JOUEUR / ENTRAINEUR / STAFF : disciplineDemandee + categorieDemandee
//     obligatoires — le couple discipline+catégorie isole les groupes
//     (ex : Football U17 ≠ Football Senior ≠ Basketball U17) ;
//   - JOURNALISTE : organismePresse (site/média) + matchId obligatoires —
//     §17 : l'accréditation est liée à un match RÉEL du calendrier, jamais
//     à un texte libre. Le serveur valide l'existence du match auprès du
//     content-service (appel interne) et stocke un libellé figé.
public record RegisterRequest(
        @NotBlank @Email String email,
        @NotBlank String phone,
        @NotBlank @Size(min = 6, max = 100) String password,
        @NotBlank String firstName,
        @NotBlank String lastName,
        String referralCode,
        String demandeRole,
        String disciplineDemandee,
        String categorieDemandee,
        String organismePresse,
        Long matchId
) {}
