package com.wydad.digital.election.repository;

import com.wydad.digital.election.model.Election;
import com.wydad.digital.election.model.ElectionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ElectionRepository extends JpaRepository<Election, Long> {

    List<Election> findByStatusOrderByStartsAtDesc(ElectionStatus status);

    List<Election> findByStatus(ElectionStatus status);
}
