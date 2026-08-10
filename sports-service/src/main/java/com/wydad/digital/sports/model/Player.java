package com.wydad.digital.sports.model;

import com.wydad.digital.sports.enums.Category;
import com.wydad.digital.sports.enums.SportType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "players")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Player {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long userId; // Linked to auth-service user

    @Column(nullable = false)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SportType sportType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Category category;

    private String position;
    private Integer jerseyNumber;
    
    private Double height; // en cm
    private Double weight; // en kg
    private Double bmi;    // calculated BMI

    private LocalDate birthDate;
    private String nationality;

    // Stats de base
    @Builder.Default private Integer matchesPlayed = 0;
    @Builder.Default private Integer goals = 0;
    @Builder.Default private Integer assists = 0;

    @CreationTimestamp private LocalDateTime createdAt;
    @UpdateTimestamp private LocalDateTime updatedAt;
}
