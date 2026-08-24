package com.wydad.digital.notification.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * Fonctionnalité 4/6 — Préférences de notification d'un membre.
 * Modèle opt-out : sans ligne pour cet utilisateur, tous les canaux sont
 * actifs. La préférence est appliquée À L'ENVOI par NotificationOrchestrator,
 * quel que soit le point d'entrée (ADMIN, broadcast, interne service-a-service).
 */
@Entity
@Table(name = "notification_preferences")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class NotificationPreference {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Un seul jeu de préférences par utilisateur. */
    @Column(nullable = false, unique = true)
    private Long userId;

    @Builder.Default
    private Boolean emailEnabled = true;

    @Builder.Default
    private Boolean pushEnabled = true;

    @Builder.Default
    private Boolean inAppEnabled = true;
}
