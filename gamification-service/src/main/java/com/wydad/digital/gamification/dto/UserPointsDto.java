package com.wydad.digital.gamification.dto;
import lombok.Builder;
import lombok.Data;

@Data @Builder
public class UserPointsDto {
    private Long userId;
    private Integer totalPoints;
    private Integer level;
    private Integer pointsToNextLevel;
}
