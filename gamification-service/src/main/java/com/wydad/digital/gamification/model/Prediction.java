package com.wydad.digital.gamification.model;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "predictions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Prediction {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private Long matchId; // From content-service / sports-service
    
    private Integer predictedHomeScore;
    private Integer predictedAwayScore;

    @Builder.Default
    private String status = "PENDING"; // PENDING, WON, LOST
    
    @Builder.Default
    private Integer pointsEarned = 0;

    @CreationTimestamp
    private LocalDateTime predictedAt;
}
