package com.wydad.digital.content.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "media")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Media {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fileName;

    @Column(nullable = false)
    private String originalName;

    @Column(nullable = false)
    private String contentType;

    private Long size;

    // Pas de @Lob : sous Hibernate 6 + PostgreSQL, @Lob mappe byte[] vers le
    // type OID (large object) alors que la colonne est BYTEA -> l'insertion
    // echoue ("column data is of type bytea but expression is of type bigint").
    // Un byte[] sans @Lob mappe nativement vers BYTEA.
    @Column(columnDefinition = "BYTEA", nullable = false)
    private byte[] data;

    @CreationTimestamp
    private LocalDateTime uploadedAt;
}
