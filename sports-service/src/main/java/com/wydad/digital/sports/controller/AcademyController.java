package com.wydad.digital.sports.controller;

import com.wydad.digital.sports.dto.AcademyMemberDto;
import com.wydad.digital.sports.filter.SportsUserContext;
import com.wydad.digital.sports.model.AcademyDocument;
import com.wydad.digital.sports.repository.AcademyDocumentRepository;
import com.wydad.digital.sports.repository.AcademyMemberRepository;
import com.wydad.digital.sports.service.AcademyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/sports/academy")
@RequiredArgsConstructor
public class AcademyController {

    private final AcademyService academyService;
    private final AcademyMemberRepository academyMemberRepository;
    private final AcademyDocumentRepository academyDocumentRepository;

    /** Types MIME acceptés pour les pièces justificatives (images + PDF). */
    private static final Set<String> ALLOWED_DOC_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "application/pdf");

    /** Taille max par pièce justificative (5 Mo, cf. mention front). */
    private static final long MAX_DOC_SIZE = 5 * 1024 * 1024;

    /** Types de pièces rattachables à un dossier. */
    private static final Set<String> ALLOWED_DOC_KINDS = Set.of(
            "BIRTH_CERTIFICATE", "MEDICAL_CERTIFICATE", "PHOTO");

    /** Inscription d'un enfant : réservée aux PARENT (et ADMIN). */
    @PostMapping("/register")
    @PreAuthorize("hasRole('PARENT') or hasRole('ADMIN')")
    public ResponseEntity<AcademyMemberDto> registerChild(@Valid @RequestBody AcademyMemberDto dto) {
        // Un parent ne peut inscrire un enfant que pour lui-même (anti-IDOR)
        if (!SportsUserContext.isAdmin()
                && dto.getParentUserId() != null
                && !dto.getParentUserId().equals(SportsUserContext.getCurrentUserId())) {
            throw new AccessDeniedException("Inscription pour un autre parent interdite");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(academyService.registerChild(dto));
    }

    @GetMapping("/parent/{parentUserId}")
    @PreAuthorize("hasRole('PARENT') or hasRole('STAFF') or hasRole('ADMIN')")
    public ResponseEntity<List<AcademyMemberDto>> getChildrenByParent(@PathVariable Long parentUserId) {
        if (!SportsUserContext.isAdmin() && !parentUserId.equals(SportsUserContext.getCurrentUserId())) {
            throw new AccessDeniedException("Accès aux enfants d'un autre parent interdit");
        }
        return ResponseEntity.ok(academyService.getChildrenByParent(parentUserId));
    }

    /** Liste globale des dossiers d'inscription : STAFF/ADMIN uniquement. */
    @GetMapping("/all")
    @PreAuthorize("hasRole('STAFF') or hasRole('ADMIN')")
    public ResponseEntity<List<AcademyMemberDto>> getAllFolders() {
        return ResponseEntity.ok(academyService.getAllFolders());
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('STAFF') or hasRole('ADMIN')")
    public ResponseEntity<AcademyMemberDto> updateStatus(@PathVariable Long id, @RequestParam Boolean active) {
        return ResponseEntity.ok(academyService.updateChildStatus(id, active));
    }

    /**
     * 0-BIS.6 : upload d'une pièce justificative (extrait de naissance,
     * certificat médical, photo) rattachée à un dossier d'inscription.
     * Le certificat médical était saisi dans le front mais jamais transmis
     * au backend. Whitelist MIME + limite de taille + propriété vérifiée
     * côté serveur (le dossier doit appartenir au parent connecté).
     */
    @PostMapping(value = "/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('PARENT') or hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> uploadDocument(
            @PathVariable Long id,
            @RequestParam("docType") String docType,
            @RequestParam("file") MultipartFile file) throws IOException {

        if (!ALLOWED_DOC_KINDS.contains(docType)) {
            throw new IllegalArgumentException("Type de document inconnu : " + docType);
        }
        if (file.isEmpty() || file.getContentType() == null
                || !ALLOWED_DOC_TYPES.contains(file.getContentType())
                || file.getSize() > MAX_DOC_SIZE) {
            throw new IllegalArgumentException(
                    "Fichier refusé : image (JPEG/PNG/WebP) ou PDF de 5 Mo maximum");
        }

        var member = academyMemberRepository.findById(id)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Dossier non trouvé"));

        // Un parent ne joint des pièces qu'à ses propres dossiers (anti-IDOR)
        Long ownerId = member.getParentUserId();
        if (!SportsUserContext.isAdmin() && !ownerId.equals(SportsUserContext.getCurrentUserId())) {
            throw new AccessDeniedException("Ce dossier appartient à un autre parent");
        }

        // Un seul document par type : ré-upload = remplacement
        academyDocumentRepository.findByAcademyMemberIdAndDocType(id, docType)
                .ifPresent(academyDocumentRepository::delete);

        AcademyDocument doc = AcademyDocument.builder()
                .academyMemberId(id)
                .docType(docType)
                .fileName(UUID.randomUUID() + "_" + file.getOriginalFilename())
                .contentType(file.getContentType())
                .size(file.getSize())
                .data(file.getBytes())
                .ownerUserId(ownerId)
                .build();
        academyDocumentRepository.save(doc);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "docType", docType,
                "fileName", doc.getFileName(),
                "size", String.valueOf(doc.getSize())));
    }

    /** Liste les pièces d'un dossier (sans les blobs), parent propriétaire ou staff/admin. */
    @GetMapping("/{id}/documents")
    @PreAuthorize("hasRole('PARENT') or hasRole('STAFF') or hasRole('ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> listDocuments(@PathVariable Long id) {
        assertFolderVisible(id);
        return ResponseEntity.ok(academyDocumentRepository.findByAcademyMemberId(id).stream()
                .map(d -> Map.<String, Object>of(
                        "docType", d.getDocType(),
                        "fileName", d.getFileName(),
                        "contentType", d.getContentType(),
                        "size", d.getSize()))
                .toList());
    }

    /** Téléchargement d'une pièce par type ; visible par le parent propriétaire ou le staff. */
    @GetMapping("/{id}/documents/{docType}")
    @PreAuthorize("hasRole('PARENT') or hasRole('STAFF') or hasRole('ADMIN')")
    public ResponseEntity<byte[]> downloadDocument(@PathVariable Long id, @PathVariable String docType) {
        assertFolderVisible(id);
        AcademyDocument doc = academyDocumentRepository
                .findByAcademyMemberIdAndDocType(id, docType.toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Document non trouvé"));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(doc.getContentType()));
        headers.setContentLength(doc.getSize());
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + doc.getFileName() + "\"");
        return new ResponseEntity<>(doc.getData(), headers, HttpStatus.OK);
    }

    /** Le dossier doit appartenir au parent connecté (STAFF/ADMIN passent). */
    private void assertFolderVisible(Long folderId) {
        var member = academyMemberRepository.findById(folderId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Dossier non trouvé"));
        if (!SportsUserContext.isAdmin()
                && !"STAFF".equals(SportsUserContext.getCurrentUserRole())
                && !member.getParentUserId().equals(SportsUserContext.getCurrentUserId())) {
            throw new AccessDeniedException("Ce dossier appartient à un autre parent");
        }
    }
}
