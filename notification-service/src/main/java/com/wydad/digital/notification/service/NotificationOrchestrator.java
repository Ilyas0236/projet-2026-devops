package com.wydad.digital.notification.service;

import com.wydad.digital.notification.client.AuthServiceClient;
import com.wydad.digital.notification.dto.NotificationRequest;
import com.wydad.digital.notification.enums.NotificationStatus;
import com.wydad.digital.notification.enums.NotificationType;
import com.wydad.digital.notification.model.Notification;
import com.wydad.digital.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationOrchestrator {

    private final NotificationRepository notificationRepository;
    private final EmailService emailService;
    private final PushNotificationService pushNotificationService;
    private final AuthServiceClient authServiceClient;
    private final NotificationPreferenceService preferenceService;

    public Notification processNotification(NotificationRequest request) {
        // Fonctionnalité 4/6 — la préférence du membre est appliquée À L'ENVOI,
        // quel que soit le point d'entrée (ADMIN /send, broadcast, /internal/send) :
        // canal désactivé -> aucune notification créée ni envoyée.
        if (request.getUserId() != null
                && !preferenceService.isChannelAllowed(request.getUserId(), request.getType().name())) {
            log.info("🔕 Envoi bloqué par préférence utilisateur {} : canal {}",
                    request.getUserId(), request.getType());
            return null;
        }

        Notification notification = Notification.builder()
                .userId(request.getUserId())
                .title(request.getTitle())
                .message(request.getMessage())
                .type(request.getType())
                .targetUrl(request.getTargetUrl())
                .imageUrl(request.getImageUrl())
                .status(NotificationStatus.PENDING)
                .build();

        notification = notificationRepository.save(notification);

        boolean success = false;
        try {
            switch (request.getType()) {
                case EMAIL:
                    if (request.getUserEmail() == null) {
                        throw new IllegalArgumentException("Email address is required for EMAIL notification");
                    }
                    success = emailService.sendEmail(request.getUserEmail(), request.getTitle(), request.getMessage());
                    break;
                case PUSH:
                    success = pushNotificationService.sendPush(request.getUserId(), request.getTitle(), request.getMessage(), request.getTargetUrl());
                    break;
                case IN_APP:
                    // IN_APP est juste sauvegardé en BDD pour être consulté plus tard via l'API (Inbox)
                    success = true;
                    log.info("📩 IN-APP Notification sauvegardée pour l'utilisateur {}", request.getUserId());
                    break;
                default:
                    log.warn("Type de notification non supporté pour le moment: {}", request.getType());
            }

            if (success) {
                notification.setStatus(NotificationStatus.SENT);
            } else {
                notification.setStatus(NotificationStatus.FAILED);
                notification.setErrorMessage("Échec de l'envoi");
            }
        } catch (Exception e) {
            notification.setStatus(NotificationStatus.FAILED);
            notification.setErrorMessage(e.getMessage());
            log.error("Erreur lors de l'envoi de la notification", e);
        }

        return notificationRepository.save(notification);
    }

    public List<Notification> getUserNotifications(Long userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }
    
    public List<Notification> getUnreadUserNotifications(Long userId) {
        return notificationRepository.findByUserIdAndStatusOrderByCreatedAtDesc(userId, NotificationStatus.SENT); // SENT means delivered but not yet READ by user
    }

    public Notification markAsRead(Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Notification non trouvée"));
        
        notification.setStatus(NotificationStatus.READ);
        return notificationRepository.save(notification);
    }
    
    public long countUnread(Long userId) {
        return notificationRepository.countByUserIdAndStatus(userId, NotificationStatus.SENT);
    }

    public Notification getById(Long notificationId) {
        return notificationRepository.findById(notificationId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Notification non trouvée"));
    }

    /** Fonctionnalité 4/6 — accès aux préférences du membre (délégation service). */
    public com.wydad.digital.notification.model.NotificationPreference getPreferences(Long userId) {
        return preferenceService.getOrCreate(userId);
    }

    public com.wydad.digital.notification.model.NotificationPreference updatePreferences(
            Long userId, boolean emailEnabled, boolean pushEnabled, boolean inAppEnabled) {
        return preferenceService.update(userId, emailEnabled, pushEnabled, inAppEnabled);
    }

    public List<Notification> getAllNotifications() {
        return notificationRepository.findAll();
    }

    /**
     * Broadcast réel : fan-out vers tous les utilisateurs actifs (via
     * auth-service, appel interne authentifié). Retourne le nombre de
     * notifications créées.
     */
    @org.springframework.transaction.annotation.Transactional
    public int broadcast(NotificationRequest template) {
        List<AuthServiceClient.Recipient> recipients = authServiceClient.fetchActiveRecipients();
        int created = 0;
        for (AuthServiceClient.Recipient recipient : recipients) {
            NotificationRequest request = new NotificationRequest();
            request.setUserId(recipient.userId());
            request.setTitle(template.getTitle());
            request.setMessage(template.getMessage());
            request.setType(template.getType());
            request.setUserEmail(recipient.email());
            request.setTargetUrl(template.getTargetUrl());
            request.setImageUrl(template.getImageUrl());
            // null = membre ayant désactivé ce canal : il n'est pas compté.
            if (processNotification(request) != null) {
                created++;
            }
        }
        log.info("📢 Broadcast : {} notification(s) créée(s) pour {} destinataire(s) (préférences respectées)",
                created, recipients.size());
        return created;
    }
}
