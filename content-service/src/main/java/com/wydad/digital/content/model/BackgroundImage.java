package com.wydad.digital.content.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Image de fond / hero d'une page publique du site, gérée par l'admin depuis
 * {@code /admin/parametres} (onglet « Backgrounds »). Plusieurs images par
 * page sont autorisées et défilent automatiquement côté front
 * (cf. {@code BackgroundCarouselComponent}).
 * <p>
 * Table créée en JPA {@code ddl-auto: update} — pas de migration Flyway car
 * la table est neuve et le projet est en update.
 */
@Entity
@Table(name = "background_images", indexes = {
        @Index(name = "idx_bg_page", columnList = "pageKey")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BackgroundImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Clé logique de page : "HOME", "BILLETTERIE", "PALMARES", "STADE",
     *  "EFFECTIF", "LEGENDES", "ACTUALITES", "CLASSEMENT" (cf. PageKey). */
    @Column(nullable = false, length = 50)
    private String pageKey;

    /** URL relative (/api/content/media/xxx) ou externe (https://...). */
    @Column(nullable = false, length = 500)
    private String imageUrl;

    /** Légende affichée sous l'image (optionnel, ex. "Wydad vs Raja — finale 2022"). */
    @Column(length = 200)
    private String caption;

    /** Ordre d'affichage (croissant). */
    @Builder.Default
    private Integer displayOrder = 0;

    /** Faux = masqué côté vitrine (caché mais conservé en BDD). */
    @Builder.Default
    private boolean active = true;

    @CreationTimestamp
    private LocalDateTime uploadedAt;

    /** Clés de page cibles — gardé en constantes pour réutilisation front/back. */
    public static final String KEY_HOME = "HOME";
    public static final String KEY_BILLETTERIE = "BILLETTERIE";
    public static final String KEY_PALMARES = "PALMARES";
    public static final String KEY_STADE = "STADE";
    public static final String KEY_EFFECTIF = "EFFECTIF";
    public static final String KEY_LEGENDES = "LEGENDES";
    public static final String KEY_ACTUALITES = "ACTUALITES";
    public static final String KEY_CLASSEMENT = "CLASSEMENT";
}
