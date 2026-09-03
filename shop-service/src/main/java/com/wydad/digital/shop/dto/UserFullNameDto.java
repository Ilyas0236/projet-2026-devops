package com.wydad.digital.shop.dto;

/**
 * Vue minimale d'un utilisateur telle que retournée par
 * {@code GET /api/auth/admin/users} côté auth-service.
 * On ne garde que les champs nécessaires au listing admin
 * des commandes (email → fullName).
 */
public record UserFullNameDto(
        String email,
        String firstName,
        String lastName
) {}
