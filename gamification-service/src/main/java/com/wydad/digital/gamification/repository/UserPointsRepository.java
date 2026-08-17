package com.wydad.digital.gamification.repository;
import com.wydad.digital.gamification.model.UserPoints;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface UserPointsRepository extends JpaRepository<UserPoints, Long> {
    List<UserPoints> findTop50ByOrderByTotalPointsDesc();
}
