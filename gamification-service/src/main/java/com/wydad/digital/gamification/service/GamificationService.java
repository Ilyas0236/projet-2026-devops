package com.wydad.digital.gamification.service;

import com.wydad.digital.gamification.dto.PredictionRequest;
import com.wydad.digital.gamification.dto.UserPointsDto;
import com.wydad.digital.gamification.model.Prediction;
import com.wydad.digital.gamification.model.UserPoints;
import com.wydad.digital.gamification.repository.PredictionRepository;
import com.wydad.digital.gamification.repository.UserPointsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GamificationService {
    private final UserPointsRepository userPointsRepository;
    private final PredictionRepository predictionRepository;

    public UserPointsDto getUserPoints(Long userId) {
        UserPoints points = userPointsRepository.findById(userId)
                .orElse(UserPoints.builder().userId(userId).totalPoints(0).level(1).build());
        
        int pointsToNextLevel = (points.getLevel() * 500) - points.getTotalPoints();
        if (pointsToNextLevel < 0) pointsToNextLevel = 0;

        return UserPointsDto.builder()
                .userId(points.getUserId())
                .totalPoints(points.getTotalPoints())
                .level(points.getLevel())
                .pointsToNextLevel(pointsToNextLevel)
                .build();
    }

    public List<UserPoints> getLeaderboard() {
        return userPointsRepository.findTop50ByOrderByTotalPointsDesc();
    }

    @Transactional
    public Prediction submitPrediction(PredictionRequest request) {
        if (predictionRepository.existsByUserIdAndMatchId(request.getUserId(), request.getMatchId())) {
            throw new RuntimeException("Pronostic déjà soumis pour ce match");
        }

        Prediction prediction = Prediction.builder()
                .userId(request.getUserId())
                .matchId(request.getMatchId())
                .predictedHomeScore(request.getPredictedHomeScore())
                .predictedAwayScore(request.getPredictedAwayScore())
                .build();

        // Bonus: 10 points for just predicting
        addPoints(request.getUserId(), 10);

        return predictionRepository.save(prediction);
    }

    public List<Prediction> getUserPredictions(Long userId) {
        return predictionRepository.findByUserIdOrderByPredictedAtDesc(userId);
    }

    @Transactional
    public void addPoints(Long userId, int amount) {
        UserPoints points = userPointsRepository.findById(userId)
                .orElse(UserPoints.builder().userId(userId).totalPoints(0).level(1).build());
        
        points.setTotalPoints(points.getTotalPoints() + amount);
        
        // Level up logic (every 500 points = 1 level)
        int newLevel = (points.getTotalPoints() / 500) + 1;
        points.setLevel(newLevel);
        
        userPointsRepository.save(points);
    }
}
