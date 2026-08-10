package com.wydad.digital.sports.repository;

import com.wydad.digital.sports.enums.Category;
import com.wydad.digital.sports.enums.SportType;
import com.wydad.digital.sports.model.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SessionRepository extends JpaRepository<Session, Long> {
    List<Session> findBySportTypeAndCategoryOrderBySessionDateAsc(SportType sportType, Category category);
    List<Session> findByCreatedByStaffIdOrderBySessionDateDesc(Long staffId);
}
