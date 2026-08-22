package com.wydad.digital.notification.controller;

import com.wydad.digital.notification.dto.NotificationRequest;
import com.wydad.digital.notification.filter.UserContext;
import com.wydad.digital.notification.model.Notification;
import com.wydad.digital.notification.service.NotificationOrchestrator;
import com.wydad.digital.notification.config.InternalSecretValidator;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notification")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationOrchestrator orchestrator;
    private final InternalSecretValidator internalSecretValidator;

    @PostMapping("/send")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Notification> sendNotification(@Valid @RequestBody NotificationRequest request) {
        Notification notification = orchestrator.processNotification(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(notification);
    }

    @PostMapping("/broadcast")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> broadcastNotification(@Valid @RequestBody NotificationRequest request) {
        // Fan-out réel vers tous les utilisateurs actifs (via auth-service)
        int created = orchestrator.broadcast(request);
        return ResponseEntity.accepted().body("Broadcast effectué : " + created + " notification(s) créée(s)");
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Notification>> getUserNotifications(@PathVariable Long userId) {
        assertSelfOrAdmin(userId);
        return ResponseEntity.ok(orchestrator.getUserNotifications(userId));
    }

    @GetMapping("/user/{userId}/unread")
    public ResponseEntity<List<Notification>> getUnreadUserNotifications(@PathVariable Long userId) {
        assertSelfOrAdmin(userId);
        return ResponseEntity.ok(orchestrator.getUnreadUserNotifications(userId));
    }

    @GetMapping("/user/{userId}/unread/count")
    public ResponseEntity<Long> getUnreadCount(@PathVariable Long userId) {
        assertSelfOrAdmin(userId);
        return ResponseEntity.ok(orchestrator.countUnread(userId));
    }

    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<Notification> markAsRead(@PathVariable Long notificationId) {
        Notification notification = orchestrator.getById(notificationId);
        assertSelfOrAdmin(notification.getUserId());
        return ResponseEntity.ok(orchestrator.markAsRead(notificationId));
    }

    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Notification>> getAllNotifications() {
        return ResponseEntity.ok(orchestrator.getAllNotifications());
    }

    /**
     * Endpoint interne service-a-service (shop/ticket -> notification).
     * Protege par un secret partage (X-Internal-Secret) : jamais expose
     * via la gateway, uniquement sur le reseau Docker interne.
     */
    @PostMapping("/internal/send")
    public ResponseEntity<Notification> internalSend(
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret,
            @Valid @RequestBody NotificationRequest request) {
        if (!internalSecretValidator.isInternalCallAuthorized(secret)) {
            throw new AccessDeniedException("Secret interne invalide");
        }
        Notification notification = orchestrator.processNotification(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(notification);
    }

    /** Un utilisateur ne peut lire que ses notifications ; ADMIN autorisé. */
    private void assertSelfOrAdmin(Long targetUserId) {
        if (!UserContext.isAdmin() && !targetUserId.equals(UserContext.getCurrentUserId())) {
            throw new AccessDeniedException("Accès aux notifications d'un autre utilisateur interdit");
        }
    }
}
