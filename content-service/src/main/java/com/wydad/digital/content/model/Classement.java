package com.wydad.digital.content.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "classements")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Classement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Integer position;

    @Column(nullable = false)
    private String equipe;

    @Column(nullable = false)
    private Integer joues;

    @Column(nullable = false)
    private Integer gagnes;

    @Column(nullable = false)
    private Integer nuls;

    @Column(nullable = false)
    private Integer perdus;

    @Column(nullable = false)
    private Integer bp;

    @Column(nullable = false)
    private Integer bc;

    @Column(nullable = false)
    private Integer points;

    @Column(nullable = false)
    private String competition;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SportSection sport;

    @CreationTimestamp
    private LocalDateTime createdAt;
}