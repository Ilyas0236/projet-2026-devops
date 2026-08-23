package com.wydad.digital.sports.repository;

import com.wydad.digital.sports.model.Message;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    /** Conversation avec une personne : messages envoyés OU reçus, chronologiques. */
    List<Message> findBySenderUserIdAndRecipientUserIdOrRecipientUserIdAndSenderUserIdOrderByCreatedAtAsc(
            Long senderId1, Long recipientId1, Long recipientId2, Long senderId2);

    /** Derniers messages reçus (boîte de réception). */
    List<Message> findByRecipientUserIdOrderByCreatedAtDesc(Long recipientUserId);
}
