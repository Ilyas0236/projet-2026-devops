package com.wydad.digital.gamification.model;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_points")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserPoints {
    @Id
    private Long userId; // The auth user ID
    
    @Builder.Default
    private Integer totalPoints = 0;
    
    @Builder.Default
    private Integer level = 1;

    @UpdateTimestamp
    private LocalDateTime lastUpdatedAt;
}
