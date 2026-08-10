package com.wydad.digital.sports.model;

import com.wydad.digital.sports.enums.SportType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "academy_members")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AcademyMember {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long parentUserId; // Linked to auth-service user (Parent)

    @Column(nullable = false)
    private String childFullName;

    @Column(nullable = false)
    private LocalDate childBirthDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SportType sportType;

    private String level; // Débutant, Intermédiaire, Avancé
    
    @Column(length = 2000)
    private String medicalHistory;

    private String bloodType;
    private String allergies;
    private String emergencyContactName;
    private String emergencyContactPhone;

    @Builder.Default
    private Boolean active = true;

    @CreationTimestamp private LocalDateTime createdAt;
    @UpdateTimestamp private LocalDateTime updatedAt;
}
