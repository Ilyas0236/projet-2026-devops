package com.wydad.digital.sports.repository;

import com.wydad.digital.sports.enums.Category;
import com.wydad.digital.sports.enums.SportType;
import com.wydad.digital.sports.model.Announcement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {

    List<Announcement> findBySportTypeAndCategoryOrderByCreatedAtDesc(SportType sportType, Category category);

    List<Announcement> findBySportTypeIsNullOrderByCreatedAtDesc();
}
