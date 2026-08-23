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
    private final com.wydad.digital.gamification.client.ContentClient contentClient;
    private final com.wydad.digital.gamification.client.NotificationClient notificationClient;

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
            throw new IllegalArgumentException("Pronostic déjà soumis pour ce match");
        }

        // Validation service-à-service : le match doit exister, être PROGRAMME
        // et ne pas avoir commencé (sinon pronostics a posteriori = farm de points)
        contentClient.getPredictableMatch(request.getMatchId());

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

    /**
     * Résout tous les pronostics PENDING d'un match à partir du score final.
     * Appelé par content-service quand l'ADMIN saisit un résultat.
     * Barème : score exact = 25 pts, bon signe (1X2) = 10 pts, sinon 0.
     */
    @Transactional
    public int resolvePredictionsForMatch(Long matchId, int scoreWydad, int scoreAdversaire) {
        List<Prediction> pending = predictionRepository.findByMatchIdAndStatus(matchId, "PENDING");
        for (Prediction p : pending) {
            boolean exactScore = p.getPredictedHomeScore().equals(scoreWydad)
                    && p.getPredictedAwayScore().equals(scoreAdversaire);
            boolean outcome = Integer.signum(p.getPredictedHomeScore() - p.getPredictedAwayScore())
                    == Integer.signum(scoreWydad - scoreAdversaire);
            String status;
            int pts;
            if (exactScore) {
                status = "WON";
                pts = 25;
            } else if (outcome) {
                status = "WON";
                pts = 10;
            } else {
                status = "LOST";
                pts = 0;
            }
            p.setStatus(status);
            p.setPointsEarned(pts);
            if (pts > 0) {
                addPoints(p.getUserId(), pts);
                notificationClient.notifyUser(
                        p.getUserId(),
                        null,
                        "Pronostic gagnant !",
                        "Votre pronostic sur le match #" + matchId + " vous rapporte " + pts + " points !",
                        "/espace-fan");
            }
            predictionRepository.save(p);
        }
        return pending.size();
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
