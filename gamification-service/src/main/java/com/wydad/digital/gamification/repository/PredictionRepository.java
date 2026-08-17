package com.wydad.digital.gamification.repository;
import com.wydad.digital.gamification.model.Prediction;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PredictionRepository extends JpaRepository<Prediction, Long> {
    List<Prediction> findByUserIdOrderByPredictedAtDesc(Long userId);
    boolean existsByUserIdAndMatchId(Long userId, Long matchId);
}
