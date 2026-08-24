package com.wydad.digital.notification.service;

import com.wydad.digital.notification.model.NotificationPreference;
import com.wydad.digital.notification.repository.NotificationPreferenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fonctionnalité 4/6 — Lecture / mise à jour des préférences d'un membre.
 * Modèle opt-out : pas de ligne = tous les canaux actifs. La règle
 * d'autorisation (chacun ne touche qu'à SES préférences, identité issue des
 * en-têtes X-User-* de la gateway) est prouvée par NotificationPreferenceTest.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationPreferenceService {

    private final NotificationPreferenceRepository preferenceRepository;

    @Transactional
    public NotificationPreference getOrCreate(Long userId) {
        return preferenceRepository.findByUserId(userId)
                .orElseGet(() -> preferenceRepository.save(
                        NotificationPreference.builder().userId(userId).build()));
    }

    @Transactional
    public NotificationPreference update(Long userId, boolean emailEnabled,
                                         boolean pushEnabled, boolean inAppEnabled) {
        NotificationPreference pref = getOrCreate(userId);
        pref.setEmailEnabled(emailEnabled);
        pref.setPushEnabled(pushEnabled);
        pref.setInAppEnabled(inAppEnabled);
        return preferenceRepository.save(pref);
    }

    /** Le canal demandé est-il autorisé pour cet utilisateur ? */
    public boolean isChannelAllowed(Long userId, String channel) {
        return switch (channel) {
            case "EMAIL" -> getOrCreate(userId).getEmailEnabled();
            case "PUSH" -> getOrCreate(userId).getPushEnabled();
            case "IN_APP" -> getOrCreate(userId).getInAppEnabled();
            default -> true; // canal inconnu : pas de préférence applicable
        };
    }
}
