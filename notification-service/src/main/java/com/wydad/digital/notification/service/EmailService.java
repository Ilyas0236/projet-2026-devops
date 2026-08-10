package com.wydad.digital.notification.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class EmailService {

    public boolean sendEmail(String to, String subject, String body) {
        log.info("📧 MOCK SENDGRID - Envoi d'email à : {}", to);
        log.info("Sujet: {}", subject);
        log.info("Message: {}", body);
        
        // Simule un délai réseau
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Simule un succès
        return true; 
    }
}
