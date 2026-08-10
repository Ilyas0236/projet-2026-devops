package com.wydad.digital.notification.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class PushNotificationService {

    public boolean sendPush(Long userId, String title, String message, String targetUrl) {
        log.info("📱 MOCK FCM - Envoi Push Notification à l'utilisateur : {}", userId);
        log.info("Titre: {}", title);
        log.info("Message: {}", message);
        if (targetUrl != null) {
            log.info("Action URL: {}", targetUrl);
        }
        
        // Simule un délai réseau
        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Simule un succès
        return true;
    }
}
