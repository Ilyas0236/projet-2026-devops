package com.wydad.digital.payment.service;

import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import com.wydad.digital.payment.dto.DonRequest;
import com.wydad.digital.payment.model.Transaction;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;

@Service
public class RecuFiscalService {

    public byte[] generateRecu(Transaction tx, DonRequest request) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = new Document();
            PdfWriter.getInstance(document, baos);
            document.open();

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
            Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 12);
            Font boldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);

            document.add(new Paragraph("RECU FISCAL DE DON", titleFont));
            document.add(new Paragraph("Wydad Athletic Club", boldFont));
            document.add(new Paragraph(" "));
            document.add(new Paragraph("Référence : " + tx.getReference(), boldFont));
            document.add(new Paragraph("Date : " + tx.getCreatedAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")), normalFont));
            document.add(new Paragraph(" "));
            document.add(new Paragraph("Donateur : " + tx.getEmail(), normalFont));
            document.add(new Paragraph("Type de don : " + request.type(), normalFont));
            document.add(new Paragraph("Montant : " + tx.getAmount() + " MAD", boldFont));
            document.add(new Paragraph(" "));
            document.add(new Paragraph("Ce reçu atteste que le Wydad Athletic Club a bien reçu le don mentionné ci-dessus.", normalFont));
            document.add(new Paragraph("Conformément à la législation marocaine, ce don ouvre droit à une réduction d'impôt.", normalFont));

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Erreur génération reçu fiscal", e);
        }
    }
}