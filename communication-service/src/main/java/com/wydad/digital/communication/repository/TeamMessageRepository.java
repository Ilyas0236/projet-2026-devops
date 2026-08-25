package com.wydad.digital.communication.repository;

import com.wydad.digital.communication.model.TeamMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TeamMessageRepository extends JpaRepository<TeamMessage, Long> {

    List<TeamMessage> findBySportTypeAndCategoryOrderByCreatedAtDesc(
            String sportType, String category, Pageable pageable);
}
