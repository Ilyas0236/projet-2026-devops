package com.wydad.digital.auth.dto;

import jakarta.validation.constraints.*;

// S3 : plus de champ membershipLevel ici — le niveau n'est JAMAIS pris du
// client à l'inscription. Le serveur attribue le niveau de départ (ROUGE) ;
// la montée passe par POST /api/auth/upgrade après paiement.
public record RegisterRequest(
        @NotBlank @Email String email,
        @NotBlank String phone,
        @NotBlank @Size(min = 6, max = 100) String password,
        @NotBlank String firstName,
        @NotBlank String lastName,
        String referralCode
) {}