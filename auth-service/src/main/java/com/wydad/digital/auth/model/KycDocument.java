package com.wydad.digital.auth.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "kyc_documents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KycDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String documentType; // CIN, PASSEPORT, PERMIS

    @Column(nullable = false)
    private String documentNumber;

    @Column(nullable = false)
    private String filePath; // Référence du fichier : publicId Cloudinary (Phase 1) ou nom local (dégradé)

    /** URL sécurisée Cloudinary (mode cloud uniquement). */
    @Column
    private String secureUrl;

    @Column(nullable = false)
    private boolean verified = false;

    @CreationTimestamp
    private LocalDateTime uploadedAt;
}