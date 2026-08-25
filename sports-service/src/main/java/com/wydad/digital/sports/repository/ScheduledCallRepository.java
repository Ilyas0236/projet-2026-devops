package com.wydad.digital.sports.repository;

import com.wydad.digital.sports.model.ScheduledCall;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScheduledCallRepository extends JpaRepository<ScheduledCall, Long> {

    /** Appels où l'utilisateur est organisateur OU participant (son « agenda »). */
    List<ScheduledCall> findByParticipantUserIdsContainingOrOrganizerUserIdOrderByScheduledAtDesc(
            Long participantUserId, Long organizerUserId);
}
