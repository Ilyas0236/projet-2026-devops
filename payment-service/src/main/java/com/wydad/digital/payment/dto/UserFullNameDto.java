package com.wydad.digital.payment.dto;

/**
 * Vue minimale d'un utilisateur retournée par
 * {@code GET /api/auth/admin/users} (auth-service). Utilisée par
 * {@link com.wydad.digital.payment.client.UserClient} pour enrichir
 * le listing admin des dons avec le fullName.
 */
public record UserFullNameDto(
        String email,
        String firstName,
        String lastName
) {}
