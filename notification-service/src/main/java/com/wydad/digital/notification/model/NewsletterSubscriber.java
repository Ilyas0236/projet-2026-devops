package com.wydad.digital.notification.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Newsletter publique — inscription anonyme depuis le footer du site.
 * Un seul abonnement par email (unicité insensible à la casse, appliquée
 * en base ET re-vérifiée en service pour un message d'erreur clair).
 */
@Entity
@Table(name = "newsletter_subscribers",
        uniqueConstraints = @UniqueConstraint(name = "uk_newsletter_email", columnNames = "email"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class NewsletterSubscriber {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Email normalisé en minuscules au moment de la sauvegarde. */
    @Column(nullable = false, unique = true, length = 254)
    private String email;

    @Builder.Default
    private boolean active = true;

    /** Date de la dernière (ré-)inscription. */
    @Builder.Default
    private LocalDateTime subscribedAt = LocalDateTime.now();

    /**
     * Désinscription sans authentification : lien à token imprévisible,
     * jamais l'id séquentiel.
     */
    @Column(nullable = false, unique = true, length = 36)
    @Builder.Default
    private String unsubscribeToken = UUID.randomUUID().toString();

    private LocalDateTime unsubscribedAt;
}
