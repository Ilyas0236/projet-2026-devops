package com.wydad.digital.sports.model;

import com.wydad.digital.sports.enums.Category;
import com.wydad.digital.sports.enums.SportType;
import com.wydad.digital.sports.enums.StaffRole;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "staff")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Staff {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long userId; // Linked to auth-service user

    @Column(nullable = false)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StaffRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SportType sportType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Category assignedCategory;

    @CreationTimestamp private LocalDateTime createdAt;
    @UpdateTimestamp private LocalDateTime updatedAt;
}
