package com.wydad.digital.auth.dto;

import jakarta.validation.constraints.*;

// S3 : plus de champ membershipLevel ici — le niveau n'est JAMAIS pris du
// client à l'inscription. Le serveur attribue le niveau de départ (ROUGE) ;
// la montée passe par POST /api/auth/upgrade après paiement.
//
// Phase 1 ter (Phase F roadmap) : demandeRole permet à un journaliste de
// solliciter une accréditation presse dès l'inscription. Seule valeur
// acceptée côté serveur : "JOURNALISTE" (tout autre rôle reste refusé —
// un client ne choisit jamais son rôle privilégié). Le compte est alors
// créé en EN_ATTENTE, dans la file de validation admin.
public record RegisterRequest(
        @NotBlank @Email String email,
        @NotBlank String phone,
        @NotBlank @Size(min = 6, max = 100) String password,
        @NotBlank String firstName,
        @NotBlank String lastName,
        String referralCode,
        String demandeRole
) {}