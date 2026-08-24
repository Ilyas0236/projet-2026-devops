package com.wydad.digital.auth.repository;

import com.wydad.digital.auth.model.KycDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface KycDocumentRepository extends JpaRepository<KycDocument, Long> {
    Optional<KycDocument> findByEmail(String email);

    void deleteByEmail(String email);
}