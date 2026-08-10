package com.wydad.digital.notification.controller;

import com.wydad.digital.notification.dto.NotificationRequest;
import com.wydad.digital.notification.model.Notification;
import com.wydad.digital.notification.service.NotificationOrchestrator;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notification")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationOrchestrator orchestrator;

    @PostMapping("/send")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Notification> sendNotification(@Valid @RequestBody NotificationRequest request) {
        Notification notification = orchestrator.processNotification(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(notification);
    }

    @PostMapping("/broadcast")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> broadcastNotification(@Valid @RequestBody NotificationRequest request) {
        // En vrai, il faudrait récupérer tous les users et envoyer en batch (RabbitMQ/Kafka)
        // Pour le MVP, on simule juste l'acceptation de la requête
        return ResponseEntity.accepted().body("Broadcast planifié");
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Notification>> getUserNotifications(@PathVariable Long userId) {
        return ResponseEntity.ok(orchestrator.getUserNotifications(userId));
    }
    
    @GetMapping("/user/{userId}/unread")
    public ResponseEntity<List<Notification>> getUnreadUserNotifications(@PathVariable Long userId) {
        return ResponseEntity.ok(orchestrator.getUnreadUserNotifications(userId));
    }
    
    @GetMapping("/user/{userId}/unread/count")
    public ResponseEntity<Long> getUnreadCount(@PathVariable Long userId) {
        return ResponseEntity.ok(orchestrator.countUnread(userId));
    }

    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<Notification> markAsRead(@PathVariable Long notificationId) {
        return ResponseEntity.ok(orchestrator.markAsRead(notificationId));
    }

    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Notification>> getAllNotifications() {
        return ResponseEntity.ok(orchestrator.getAllNotifications());
    }
}
