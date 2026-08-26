package com.wydad.digital.auth.controller;

import com.wydad.digital.auth.model.SalaryReceipt;
import com.wydad.digital.auth.repository.UserRepository;
import com.wydad.digital.auth.service.SalaryReceiptService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * Espace Président — reçus PDF de salaires et primes (§11-§15).
 *
 * <p>Sécurité (§24, défense-en-profondeur) : l'identité vient des headers
 * X-User-* posés par la gateway. Si ces headers sont absents, la requête
 * n'est pas passée par la gateway (appel direct au port du service) →
 * refus systématique. L'émission est réservée au PRÉSIDENT/ADMIN ; un
 * bénéficiaire ne consulte et ne télécharge que SES reçus — jamais ceux
 * d'un autre.</p>
 */
@RestController
@RequestMapping("/api/auth/salary-receipts")
@RequiredArgsConstructor
public class SalaryReceiptController {

    private final SalaryReceiptService receiptService;
    private final UserRepository userRepository;

    // ───────────────────── Émission (PRÉSIDENT / ADMIN) ─────────────────────

    @PostMapping
    @PreAuthorize("hasAnyRole('PRESIDENT','ADMIN')")
    public ResponseEntity<SalaryReceipt> emettre(
            @RequestBody EmissionRequest request,
            @RequestHeader(value = "X-User-Email", required = false) String gatewayEmail,
            @RequestHeader(value = "X-User-Role", required = false) String gatewayRole) {
        requireGatewayIdentity(gatewayEmail, gatewayRole);
        var issuer = userRepository.findByEmailIgnoreCase(gatewayEmail).orElse(null);

        var beneficiaire = userRepository.findById(request.userId())
                .orElseThrow(() -> new IllegalArgumentException("Bénéficiaire introuvable"));
        if (!beneficiaire.isActive()) {
            throw new IllegalArgumentException("Compte bénéficiaire désactivé");
        }

        SalaryReceipt receipt = receiptService.emettre(
                beneficiaire.getId(),
                beneficiaire.getFirstName() + " " + beneficiaire.getLastName(),
                beneficiaire.getEmail(),
                request.receiptType(),
                request.amount(),
                request.periode(),
                request.motif(),
                issuer != null ? issuer.getId() : null,
                issuer != null
                        ? issuer.getFirstName() + " " + issuer.getLastName()
                        : gatewayEmail);

        return ResponseEntity.status(HttpStatus.CREATED).body(receipt);
    }

    /** Tous les reçus — vue présidence. */
    @GetMapping
    @PreAuthorize("hasAnyRole('PRESIDENT','ADMIN')")
    public ResponseEntity<List<SalaryReceipt>> listAll(
            @RequestHeader(value = "X-User-Email", required = false) String gatewayEmail,
            @RequestHeader(value = "X-User-Role", required = false) String gatewayRole) {
        requireGatewayIdentity(gatewayEmail, gatewayRole);
        return ResponseEntity.ok(receiptService.listAll());
    }

    // ─────────────────────── Bénéficiaire (ownership strict) ───────────────────────

    @GetMapping("/mine")
    public ResponseEntity<List<SalaryReceipt>> mine(
            @RequestHeader(value = "X-User-Email", required = false) String gatewayEmail,
            @RequestHeader(value = "X-User-Role", required = false) String gatewayRole) {
        requireGatewayIdentity(gatewayEmail, gatewayRole);
        var me = userRepository.findByEmailIgnoreCase(gatewayEmail)
                .orElseThrow(() -> new AccessDeniedException("Compte introuvable"));
        return ResponseEntity.ok(receiptService.listForUser(me.getId()));
    }

    /** Téléchargement du PDF : bénéficiaire lui-même, président ou admin. */
    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> pdf(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Email", required = false) String gatewayEmail,
            @RequestHeader(value = "X-User-Role", required = false) String gatewayRole) {
        requireGatewayIdentity(gatewayEmail, gatewayRole);

        SalaryReceipt receipt = receiptService.listAll().stream()
                .filter(r -> r.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Reçu non trouvé"));

        boolean presidentOuAdmin = "PRESIDENT".equals(gatewayRole) || "ADMIN".equals(gatewayRole);
        if (!presidentOuAdmin) {
            var me = userRepository.findByEmailIgnoreCase(gatewayEmail)
                    .orElseThrow(() -> new AccessDeniedException("Compte introuvable"));
            if (!receipt.getUserId().equals(me.getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
        }

        byte[] pdf = receiptService.genererPdf(receipt);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=recu-" + receipt.getReference() + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    // ───────────────────────────────── Helpers ─────────────────────────────────

    /** §24 : sans headers gateway → appel direct hors passerelle → 401. */
    private static void requireGatewayIdentity(String email, String role) {
        if (email == null || role == null) {
            throw new com.wydad.digital.auth.exception.GatewayIdentityMissingException();
        }
    }

    public record EmissionRequest(
            Long userId,
            String receiptType,
            BigDecimal amount,
            String periode,
            String motif) {
    }
}
