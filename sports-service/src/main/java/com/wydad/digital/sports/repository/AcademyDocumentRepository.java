package com.wydad.digital.sports.repository;

import com.wydad.digital.sports.model.AcademyDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AcademyDocumentRepository extends JpaRepository<AcademyDocument, Long> {
    List<AcademyDocument> findByAcademyMemberId(Long academyMemberId);

    Optional<AcademyDocument> findByAcademyMemberIdAndDocType(Long academyMemberId, String docType);
}
