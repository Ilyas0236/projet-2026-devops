package com.wydad.digital.sports.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Document partagé avec un joueur par le staff/admin (B.3) : référence
 * vers un fichier déjà téléversé (médiathèque). Le joueur ne peut voir
 * que les documents qui lui sont adressés.
 */
@Entity
@Table(name = "player_documents")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PlayerDocument {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** userId auth-service du joueur destinataire. */
    @Column(nullable = false)
    private Long joueurUserId;

    @Column(nullable = false)
    private String title;

    /** URL du document (médiathèque ou stockage de fichiers). */
    @Column(nullable = false)
    private String url;

    @CreationTimestamp
    private LocalDateTime dateAjout;
}
