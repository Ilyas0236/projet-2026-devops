package com.wydad.digital.sports.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Média tactique partagé avec les joueurs par le staff (Phase 3 / B.3) :
 * vidéo d'analyse, photo de tableau tactique ou document PDF. Le fichier est
 * stocké sur Cloudinary (folder privé) — seuls identifiant + URL sécurisée
 * sont conservés. Le joueur ne peut voir que les médias qui lui sont adressés.
 */
@Entity
@Table(name = "player_documents")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PlayerDocument {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** userId auth-service du staff/admin émetteur (traçabilité). */
    private Long senderUserId;

    /** Type de média : VIDEO / PHOTO / DOCUMENT. */
    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private MediaType mediaType;

    /**
     * userIds auth-service des joueurs destinataires — un seul joueur OU
     * toute la catégorie (« envoi équipe »). Vide = adressé au joueur de
     * {@code joueurUserId} (compatibilité historique).
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "player_document_recipients", joinColumns = @JoinColumn(name = "document_id"))
    @Column(name = "recipient_user_id", nullable = false)
    private Set<Long> recipientUserIds = new HashSet<>();

    /**
     * Destinataire historique (1 joueur). Conservé pour compat : vaut le
     * destinataire unique quand l'envoi est individuel.
     */
    @Column(nullable = false)
    private Long joueurUserId;

    @Column(nullable = false)
    private String title;

    /** Message joint optionnel (consigne de l'entraîneur). */
    @Column(length = 2000)
    private String message;

    /** URL du fichier (Cloudinary ou référence locale en mode dégradé). */
    @Column(nullable = false)
    private String url;

    /** Identifiant Cloudinary (null en mode dégradé local). */
    private String publicId;

    /**
     * Phase 3 — envoi « équipe entière » : catégorie/sport cibles
     * dénormalisés pour l'affichage liste (null si envoi individuel).
     */
    @Enumerated(EnumType.STRING) private com.wydad.digital.sports.enums.SportType sportType;
    @Enumerated(EnumType.STRING) private com.wydad.digital.sports.enums.Category category;
    private boolean wholeTeam;

    @CreationTimestamp
    private LocalDateTime dateAjout;

    public enum MediaType { VIDEO, PHOTO, DOCUMENT }
}
