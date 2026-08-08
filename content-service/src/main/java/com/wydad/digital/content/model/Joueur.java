package com.wydad.digital.content.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "joueurs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Joueur {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nom;

    private String photoUrl;

    @Column(nullable = false)
    private String poste;

    @Column(nullable = false)
    private Integer age;

    @Column(nullable = false)
    private Integer numero;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SportSection sport;

    private Integer matchsJoues;

    private Integer buts;

    private Integer passes;

    @CreationTimestamp
    private LocalDateTime createdAt;
}