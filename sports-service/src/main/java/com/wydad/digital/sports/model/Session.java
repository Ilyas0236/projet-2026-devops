package com.wydad.digital.sports.model;

import com.wydad.digital.sports.enums.Category;
import com.wydad.digital.sports.enums.SportType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "sessions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Session {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(length = 1000)
    private String description;
    
    private String location;

    @Column(nullable = false)
    private LocalDateTime sessionDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SportType sportType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Category category;

    @Column(nullable = false)
    private Long createdByStaffId; // Reference to Staff entity ID

    @CreationTimestamp private LocalDateTime createdAt;
    @UpdateTimestamp private LocalDateTime updatedAt;
}
