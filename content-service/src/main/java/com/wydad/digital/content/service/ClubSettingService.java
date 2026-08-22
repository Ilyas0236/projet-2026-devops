package com.wydad.digital.content.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wydad.digital.content.model.ClubSetting;
import com.wydad.digital.content.repository.ClubSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Gestion des parametres du club (source de verite ADMIN).
 * Les valeurs sont stockees en JSON et servies telles quelles au frontend,
 * qui n'a plus aucune donnee metier hardcodee (paliers d'adhesion, contacts...).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClubSettingService {

    public static final String KEY_MEMBERSHIP_TIERS = "membership_tiers";
    public static final String KEY_CLUB_INFO = "club_info";

    private final ClubSettingRepository clubSettingRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<ClubSetting> getAllSettings() {
        return clubSettingRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Object getSetting(String key) {
        return clubSettingRepository.findBySettingKey(key)
                .map(this::parseValue)
                .orElse(null);
    }

    /** Cree ou remplace la valeur d'un parametre. */
    @Transactional
    public ClubSetting upsertSetting(String key, Object value) {
        if (value == null) {
            throw new IllegalArgumentException("Valeur requise");
        }
        String json = toJson(value);
        validateJsonRoot(json);

        ClubSetting setting = clubSettingRepository.findBySettingKey(key)
                .orElseGet(() -> ClubSetting.builder().settingKey(key).build());
        setting.setSettingValue(json);
        return clubSettingRepository.save(setting);
    }

    @Transactional
    public void deleteSetting(String key) {
        ClubSetting setting = clubSettingRepository.findBySettingKey(key)
                .orElseThrow(() -> new IllegalArgumentException("Parametre inconnu : " + key));
        clubSettingRepository.delete(setting);
    }

    private Object parseValue(ClubSetting setting) {
        try {
            return objectMapper.readValue(setting.getSettingValue(), Object.class);
        } catch (JsonProcessingException e) {
            log.error("Valeur JSON invalide pour le parametre {}", setting.getSettingKey(), e);
            return null;
        }
    }

    /** Refuse les scalaires nus : un parametre club est toujours objet ou tableau. */
    private void validateJsonRoot(String json) {
        char first = json.trim().isEmpty() ? ' ' : json.trim().charAt(0);
        if (first != '{' && first != '[') {
            throw new IllegalArgumentException("La valeur doit etre un objet ou un tableau JSON");
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Valeur JSON invalide", e);
        }
    }
}
