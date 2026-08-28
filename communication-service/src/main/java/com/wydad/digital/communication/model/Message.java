package com.wydad.digital.communication.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Message privé joueur ↔ staff (B.5). L'appariement est vérifié côté
 * serveur : un joueur ne peut écrire qu'au staff encadrant SA catégorie,
 * et le staff ne peut écrire qu'aux joueurs de la sienne.
 */
@Entity
@Table(name = "messages")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Message {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long senderUserId;

    @Column(nullable = false)
    private String senderName;

    /** STAFF ou JOUEUR — rôle de l'expéditeur au moment de l'envoi. */
    @Column(nullable = false, length = 20)
    private String senderRole;

    @Column(nullable = false)
    private Long recipientUserId;

    @Column(nullable = false, length = 500)
    private String content;

    private LocalDateTime readAt;

    // ───────────── V2.3 — pièces jointes (Cloudinary, type authenticated) ─────────────
    /** public_id Cloudinary. Null = pas de pièce jointe. */
    @Column(length = 200)
    private String attachmentPublicId;

    /** URL sécurisée (signée) au moment de l'envoi, pour affichage immédiat. */
    @Column(length = 500)
    private String attachmentSecureUrl;

    /** "image" | "raw" | "video" — pour <img> vs lien download. */
    @Column(length = 20)
    private String attachmentResourceType;

    /** Nom de fichier d'origine (pour l'UI). */
    @Column(length = 255)
    private String attachmentFileName;

    /** Taille en octets (UI : afficher "12 Ko"). */
    private Long attachmentSizeBytes;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
