package com.wydad.digital.content.repository;

import com.wydad.digital.content.model.ClubSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ClubSettingRepository extends JpaRepository<ClubSetting, Long> {
    Optional<ClubSetting> findBySettingKey(String settingKey);
}
