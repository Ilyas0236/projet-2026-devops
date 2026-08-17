package com.wydad.digital.gamification.dto;
import lombok.Data;

@Data
public class PredictionRequest {
    private Long userId;
    private Long matchId;
    private Integer predictedHomeScore;
    private Integer predictedAwayScore;
}
