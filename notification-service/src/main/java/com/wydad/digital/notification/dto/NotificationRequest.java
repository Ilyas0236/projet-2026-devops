package com.wydad.digital.notification.dto;

import com.wydad.digital.notification.enums.NotificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class NotificationRequest {
    /**
     * Pour /internal/send : destinataire unique (obligatoire).
     * Pour /internal/broadcast-targeted : ignoré (la liste {@code targetUserIds}
     * fait foi). Pour /internal/broadcast : ignoré (fan-out à tous les actifs).
     */
    private Long userId;
    @NotBlank private String title;
    @NotBlank private String message;
    @NotNull private NotificationType type;

    // Additional info for Email (if not fetched from a user service)
    private String userEmail;

    private String targetUrl;
    private String imageUrl;

    /**
     * Optionnel : whitelist d'IDs pour /internal/broadcast-targeted.
     * Si null/empty, l'orchestrateur retombe sur broadcast global.
     * Jamais nullé en sortie : reste ce que l'appelant a envoyé.
     */
    private List<Long> targetUserIds;
}
