package com.wydad.digital.ticket.repository;

import com.wydad.digital.ticket.enums.TicketCategory;
import com.wydad.digital.ticket.model.Section;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SectionRepository extends JpaRepository<Section, Long> {

    /**
     * Charge la section avec un verrou pessimiste (SELECT ... FOR UPDATE)
     * afin de sérialiser les achats/annulations concurrents de billets.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("SELECT s FROM Section s WHERE s.id = :id")
    Optional<Section> findByIdForUpdate(@Param("id") Long id);

    List<Section> findByEventId(Long eventId);
    Optional<Section> findByEventIdAndCategory(Long eventId, TicketCategory category);
}
