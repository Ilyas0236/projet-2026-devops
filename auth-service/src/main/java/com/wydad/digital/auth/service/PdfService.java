package com.wydad.digital.auth.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfWriter;
import com.wydad.digital.auth.model.User;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;

@Service
public class PdfService {

    public byte[] generateAttestation(User user) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4);
        PdfWriter.getInstance(document, outputStream);

        document.open();

        // Titre
        Font titleFont = new Font(Font.HELVETICA, 24, Font.BOLD, new Color(0xCC, 0x00, 0x00));
        Paragraph title = new Paragraph("ATTESTATION D'ADHESION", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        document.add(title);
        document.add(Chunk.NEWLINE);

        // Sous-titre
        Font subtitleFont = new Font(Font.HELVETICA, 16, Font.BOLD);
        Paragraph subtitle = new Paragraph("WYDAD ATHLETIC CLUB", subtitleFont);
        subtitle.setAlignment(Element.ALIGN_CENTER);
        document.add(subtitle);
        document.add(Chunk.NEWLINE);
        document.add(Chunk.NEWLINE);

        // Contenu
        Font normalFont = new Font(Font.HELVETICA, 12);
        Font boldFont = new Font(Font.HELVETICA, 12, Font.BOLD);

        document.add(new Paragraph("Attestation n° WAC-" + user.getId() + "-" + user.getReferralCode(), normalFont));
        document.add(Chunk.NEWLINE);

        document.add(new Paragraph("Le Wydad Athletic Club certifie que :", normalFont));
        document.add(Chunk.NEWLINE);

        Paragraph namePara = new Paragraph();
        namePara.add(new Chunk("Nom complet : ", boldFont));
        namePara.add(new Chunk(user.getFirstName() + " " + user.getLastName(), normalFont));
        document.add(namePara);

        Paragraph emailPara = new Paragraph();
        emailPara.add(new Chunk("Email : ", boldFont));
        emailPara.add(new Chunk(user.getEmail(), normalFont));
        document.add(emailPara);

        Paragraph phonePara = new Paragraph();
        phonePara.add(new Chunk("Telephone : ", boldFont));
        phonePara.add(new Chunk(user.getPhone(), normalFont));
        document.add(phonePara);

        Paragraph levelPara = new Paragraph();
        levelPara.add(new Chunk("Niveau d'adhesion : ", boldFont));
        levelPara.add(new Chunk(user.getMembershipLevel().getDisplayName() + " (" + user.getMembershipLevel().getPrice() + " DH)", normalFont));
        document.add(levelPara);

        Paragraph codePara = new Paragraph();
        codePara.add(new Chunk("Code adherent : ", boldFont));
        codePara.add(new Chunk("WAC-" + user.getReferralCode(), normalFont));
        document.add(codePara);

        document.add(Chunk.NEWLINE);
        document.add(new Paragraph("Cette attestation est delivree a titre de preuve d'adhesion au club.", normalFont));
        document.add(Chunk.NEWLINE);

        Paragraph datePara = new Paragraph();
        datePara.add(new Chunk("Date d'emission : ", boldFont));
        datePara.add(new Chunk(java.time.LocalDate.now().toString(), normalFont));
        document.add(datePara);

        document.add(Chunk.NEWLINE);
        document.add(Chunk.NEWLINE);

        // Signature
        Paragraph sign = new Paragraph("Le President", boldFont);
        sign.setAlignment(Element.ALIGN_RIGHT);
        document.add(sign);

        document.close();
        return outputStream.toByteArray();
    }
}