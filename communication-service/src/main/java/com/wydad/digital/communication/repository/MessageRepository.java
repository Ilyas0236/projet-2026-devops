package com.wydad.digital.communication.repository;

import com.wydad.digital.communication.model.Message;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    List<Message> findBySenderUserIdAndRecipientUserIdOrRecipientUserIdAndSenderUserIdOrderByCreatedAtAsc(
            Long sender1, Long recipient1, Long recipient2, Long sender2);

    List<Message> findByRecipientUserIdOrderByCreatedAtDesc(Long recipientUserId);
}
