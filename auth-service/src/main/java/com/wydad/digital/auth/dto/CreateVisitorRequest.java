package com.wydad.digital.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * B.28 — Création d'un compte VISITEUR à la volée.
 *
 * Le visiteur n'a PAS demandé de compte : il clique "Acheter" depuis la
 * billetterie publique et renseigne nom/email/téléphone. On lui crée
 * un user minimal (role=VISITEUR, statut=VALIDE, mdp généré) pour
 * pouvoir rattacher son billet à un userId, lui envoyer le PDF par
 * email, et lui permettre plus tard de réclamer son compte.
 *
 * Sécurité : cet endpoint n'est JAMAIS exposé par la gateway ; il est
 * protégé par le secret interne partagé avec les services métier.
 */
public record CreateVisitorRequest(
        @NotBlank @Email String email,
        @NotBlank String firstName,
        @NotBlank String lastName,
        @NotBlank String phone
) {}
