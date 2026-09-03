package com.wydad.digital.auth.dto;

import com.wydad.digital.auth.model.MembershipLevel;
import jakarta.validation.constraints.*;

/**
 * Corps de {@code POST /api/auth/admin/users/create} — création d'un compte
 * par l'admin. Étendu par rapport au DTO d'origine (admin) pour permettre
 * la création de comptes sportifs avec discipline + catégorie, utile
 * quand le propriétaire ne veut pas attendre la file de validation
 * auto-inscription (compte d'un joueur déjà recruté, staff technique,
 * entraîneur de section, etc.).
 *
 * <p>Pour les rôles {@code JOUEUR / ENTRAINEUR / STAFF} : discipline et
 * catégorie sont OBLIGATOIRES et validées contre les listes de référence
 * du service (cf. AuthService#DISCIPLINES_VALIDES / CATEGORIES_VALIDES).
 * Pour les autres rôles (ADHERENT, PARENT, JOURNALISTE, PRESIDENT, ADMIN,
 * VISITEUR), ces deux champs sont simplement ignorés.</p>
 */
public record AdminCreateUserRequest(
        @NotBlank @Email String email,
        String phone,
        @NotBlank @Size(min = 6, max = 100) String password,
        @NotBlank String firstName,
        @NotBlank String lastName,
        @NotBlank String role,
        MembershipLevel membershipLevel,
        /** Discipline sportive (FOOTBALL/BASKETBALL/HANDBALL/VOLLEYBALL/...). */
        String discipline,
        /** Catégorie sportive (U15/U17/U18/U20/SENIOR) — accompagne discipline. */
        String categorie
) {}
