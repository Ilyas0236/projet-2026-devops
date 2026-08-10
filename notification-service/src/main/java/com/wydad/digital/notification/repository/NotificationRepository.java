package com.wydad.digital.notification.repository;

import com.wydad.digital.notification.enums.NotificationStatus;
import com.wydad.digital.notification.enums.NotificationType;
import com.wydad.digital.notification.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<Notification> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, NotificationStatus status);
    List<Notification> findByUserIdAndTypeOrderByCreatedAtDesc(Long userId, NotificationType type);
    long countByUserIdAndStatus(Long userId, NotificationStatus status);
}
