package com.wydad.digital.sports.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Document justificatif joint à un dossier d'inscription académie
 * (extrait de naissance, certificat médical, photo). Contenu stocké
 * en BYTEA comme la médiathèque — pas de volume disque (déploiement
 * Docker-only 1 Go RAM).
 */
@Entity
@Table(name = "academy_documents")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AcademyDocument {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Dossier d'inscription rattaché. */
    @Column(nullable = false)
    private Long academyMemberId;

    /** Type de pièce : BIRTH_CERTIFICATE, MEDICAL_CERTIFICATE, PHOTO. */
    @Column(nullable = false)
    private String docType;

    @Column(nullable = false)
    private String fileName;

    @Column(nullable = false)
    private String contentType;

    @Column(nullable = false)
    private Long size;

    // Pas de @Lob : sous Hibernate 6 + PostgreSQL, un byte[] sans @Lob
    // mappe nativement vers BYTEA (voir content-service Media.java).
    @Column(nullable = false)
    private byte[] data;

    /** Parent propriétaire du dossier (contrôle d'accès sans jointure). */
    @Column(nullable = false)
    private Long ownerUserId;

    @CreationTimestamp
    private LocalDateTime uploadedAt;
}
