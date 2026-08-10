package com.wydad.digital.notification.dto;

import com.wydad.digital.notification.enums.NotificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class NotificationRequest {
    @NotNull private Long userId;
    @NotBlank private String title;
    @NotBlank private String message;
    @NotNull private NotificationType type;
    
    // Additional info for Email (if not fetched from a user service)
    private String userEmail; 
    
    private String targetUrl;
    private String imageUrl;
}
