package com.wydad.digital.content.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Rapport financier publié par l'ADMIN : visible par les adhérents dans
 * chaque interface (profil, espaces) et sur la page publique transparence.
 * Le fichier est stocké via la médiathèque existante (table media).
 */
@Entity
@Table(name = "rapports_financiers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RapportFinancier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String titre;

    /** Exercice concerné (ex : 2025). */
    @Column(nullable = false)
    private int annee;

    @Column(length = 1000)
    private String description;

    /** URL relative du PDF dans la médiathèque (/api/content/media/xxx.pdf). */
    @Column(nullable = false, length = 500)
    private String fileUrl;

    /** Nom original du fichier, affiché au téléchargement. */
    @Column(length = 300)
    private String originalName;

    @Column(nullable = false)
    private String publiePar; // email de l'admin

    @CreationTimestamp
    private LocalDateTime publieLe;
}
