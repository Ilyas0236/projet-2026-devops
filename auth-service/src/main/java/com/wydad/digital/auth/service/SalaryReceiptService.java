package com.wydad.digital.auth.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfWriter;
import com.wydad.digital.auth.model.SalaryReceipt;
import com.wydad.digital.auth.repository.SalaryReceiptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Phase 5 bis — reçus PDF de salaires/primes émis par le Président.
 *
 * <p>Règles serveur (§ sécurité) :</p>
 * <ul>
 *   <li>émission réservée au PRÉSIDENT (et à l'ADMIN) ;</li>
 *   <li>le bénéficiaire ne voit et ne télécharge que SES reçus ;</li>
 *   <li>le PDF est généré à la volée (OpenPDF), seules les métadonnées
 *       sont persistées — rien de stocké sur Cloudinary, aucune fuite.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class SalaryReceiptService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final SalaryReceiptRepository receiptRepository;

    /** Types de reçus acceptés. */
    public static boolean isTypeValide(String type) {
        return "SALAIRE".equals(type) || "PRIME".equals(type);
    }

    @Transactional
    public SalaryReceipt emettre(Long userId, String userFullName, String userEmail,
                                 String type, BigDecimal amount, String periode,
                                 String motif, Long issuedByUserId, String issuedByName) {
        if (!isTypeValide(type)) {
            throw new IllegalArgumentException("Type de reçu invalide (SALAIRE ou PRIME)");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Le montant doit être strictement positif");
        }
        if ("SALAIRE".equals(type) && (periode == null || periode.isBlank())) {
            throw new IllegalArgumentException("La période est obligatoire pour un salaire");
        }

        long count = receiptRepository.count();
        SalaryReceipt receipt = receiptRepository.save(SalaryReceipt.builder()
                .userId(userId)
                .userFullName(userFullName)
                .userEmail(userEmail)
                .receiptType(type)
                .amount(amount)
                .currency("MAD")
                .periode(periode != null && !periode.isBlank() ? periode.trim() : null)
                .motif(motif != null && !motif.isBlank() ? motif.trim() : null)
                .reference(String.format("WAC-REC-%d-%06d", LocalDate.now().getYear(), count + 1))
                .paymentDate(LocalDate.now())
                .issuedByUserId(issuedByUserId)
                .issuedByName(issuedByName)
                .build());

        return receipt;
    }

    /** Liste complète — président/admin uniquement (re-vérifié au contrôleur). */
    public java.util.List<SalaryReceipt> listAll() {
        return receiptRepository.findAllByOrderByPaymentDateDesc();
    }

    /** Reçus d'un bénéficiaire — ownership vérifié par le contrôleur. */
    public java.util.List<SalaryReceipt> listForUser(Long userId) {
        return receiptRepository.findByUserIdOrderByPaymentDateDesc(userId);
    }

    /** Reçu téléchargeable — l'appelant doit être bénéficiaire, président ou admin. */
    public byte[] genererPdf(SalaryReceipt receipt) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, out);
            document.open();

            Font titleFont = new Font(Font.HELVETICA, 22, Font.BOLD,
                    new java.awt.Color(0xCC, 0x00, 0x00));
            Paragraph title = new Paragraph(
                    "RECU DE " + ("SALAIRE".equals(receipt.getReceiptType()) ? "SALAIRE" : "PRIME"),
                    titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            document.add(title);

            Font clubFont = new Font(Font.HELVETICA, 14, Font.BOLD);
            Paragraph club = new Paragraph("WYDAD ATHLETIC CLUB", clubFont);
            club.setAlignment(Element.ALIGN_CENTER);
            document.add(club);
            document.add(Chunk.NEWLINE);

            Font normal = new Font(Font.HELVETICA, 12);
            Font bold = new Font(Font.HELVETICA, 12, Font.BOLD);

            document.add(new Paragraph("Référence : " + receipt.getReference(), bold));
            document.add(Chunk.NEWLINE);

            Paragraph beneficiaire = new Paragraph();
            beneficiaire.add(new Chunk("Bénéficiaire : ", bold));
            beneficiaire.add(new Chunk(receipt.getUserFullName(), normal));
            document.add(beneficiaire);

            Paragraph emailPara = new Paragraph();
            emailPara.add(new Chunk("Email : ", bold));
            emailPara.add(new Chunk(receipt.getUserEmail(), normal));
            document.add(emailPara);

            if (receipt.getPeriode() != null) {
                Paragraph p = new Paragraph();
                p.add(new Chunk("Période : ", bold));
                p.add(new Chunk(receipt.getPeriode(), normal));
                document.add(p);
            }
            if (receipt.getMotif() != null) {
                Paragraph p = new Paragraph();
                p.add(new Chunk("Motif : ", bold));
                p.add(new Chunk(receipt.getMotif(), normal));
                document.add(p);
            }

            Paragraph montant = new Paragraph();
            montant.add(new Chunk("Montant net versé : ", bold));
            montant.add(new Chunk(receipt.getAmount().toPlainString() + " " + receipt.getCurrency(),
                    new Font(Font.HELVETICA, 14, Font.BOLD)));
            document.add(montant);

            Paragraph date = new Paragraph();
            date.add(new Chunk("Date de paiement : ", bold));
            date.add(new Chunk(receipt.getPaymentDate() != null
                    ? receipt.getPaymentDate().format(DATE_FMT) : "-", normal));
            document.add(date);

            document.add(Chunk.NEWLINE);
            document.add(new Paragraph(
                    "Reçu délivré par la présidence du Wydad Athletic Club à titre de preuve "
                            + "de paiement. Document personnel et confidentiel.", normal));

            document.add(Chunk.NEWLINE);
            Paragraph emetteur = new Paragraph();
            emetteur.add(new Chunk("Émis par : ", bold));
            emetteur.add(new Chunk(receipt.getIssuedByName() + " — Présidence WAC", normal));
            emetteur.setAlignment(Element.ALIGN_RIGHT);
            document.add(emetteur);

            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Erreur lors de la génération du reçu PDF", e);
        }
    }
}
