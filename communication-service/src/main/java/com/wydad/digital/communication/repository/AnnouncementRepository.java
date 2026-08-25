package com.wydad.digital.communication.repository;

import com.wydad.digital.communication.model.Announcement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {

    List<Announcement> findBySportTypeIsNullOrderByCreatedAtDesc();

    List<Announcement> findBySportTypeAndCategoryOrderByCreatedAtDesc(String sportType, String category);
}
