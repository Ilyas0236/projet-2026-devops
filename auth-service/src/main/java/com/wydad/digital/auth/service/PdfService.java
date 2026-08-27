package com.wydad.digital.auth.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfWriter;
import com.wydad.digital.auth.model.User;
import com.wydad.digital.auth.model.subscription.UserSubscription;
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

    /**
     * Accréditation presse — badge officiel PDF avec QR code. Le QR encode
     * l'identité complète (nom, prénom, organe de presse) pour vérification
     * à l'entrée du stade. Généré uniquement pour un JOURNALISTE validé.
     */
    public byte[] generateBadgePresse(User user, String qrBase64) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        // Format paysage carte (proche du format badge plastique 85x54mm agrandi)
        Document document = new Document(new RectangleReadOnly(560, 350));
        PdfWriter.getInstance(document, outputStream);

        document.open();

        // Bande rouge supérieure — identité visuelle du club
        Rectangle band = new Rectangle(0, 290, 560, 350);
        band.setBackgroundColor(new Color(0xDC, 0x14, 0x3C)); // rouge Wydad
        document.add(band);

        Font clubFont = new Font(Font.HELVETICA, 20, Font.BOLD, Color.WHITE);
        Paragraph club = new Paragraph("WYDAD ATHLETIC CLUB", clubFont);
        club.setAlignment(Element.ALIGN_CENTER);
        document.add(club);

        Font accFont = new Font(Font.HELVETICA, 12, Font.NORMAL, Color.WHITE);
        Paragraph accTitle = new Paragraph("ACCREDITATION PRESSE OFFICIELLE", accFont);
        accTitle.setAlignment(Element.ALIGN_CENTER);
        accTitle.setSpacingBefore(4);
        document.add(accTitle);

        document.add(Chunk.NEWLINE);

        Font labelFont = new Font(Font.HELVETICA, 9, Font.BOLD, new Color(0x88, 0x88, 0x88));
        Font valueFont = new Font(Font.HELVETICA, 15, Font.BOLD, new Color(0x1A, 0x1A, 0x2E));

        Paragraph nomLabel = new Paragraph("JOURNALISTE", labelFont);
        nomLabel.setIndentationLeft(30);
        document.add(nomLabel);
        Paragraph nom = new Paragraph(user.getFirstName() + " " + user.getLastName(), valueFont);
        nom.setIndentationLeft(30);
        nom.setSpacingAfter(10);
        document.add(nom);

        if (user.getOrganismePresse() != null && !user.getOrganismePresse().isBlank()) {
            Paragraph organeLabel = new Paragraph("ORGANE DE PRESSE", labelFont);
            organeLabel.setIndentationLeft(30);
            document.add(organeLabel);
            Paragraph organe = new Paragraph(user.getOrganismePresse(), valueFont);
            organe.setIndentationLeft(30);
            organe.setSpacingAfter(10);
            document.add(organe);
        }

        if (user.getMatchSouhaite() != null && !user.getMatchSouhaite().isBlank()) {
            Paragraph matchLabel = new Paragraph("MATCH COUVERT", labelFont);
            matchLabel.setIndentationLeft(30);
            document.add(matchLabel);
            Paragraph matchP = new Paragraph(user.getMatchSouhaite(), valueFont);
            matchP.setIndentationLeft(30);
            matchP.setSpacingAfter(6);
            document.add(matchP);
        }

        // QR code à droite : contient nom + prénom + organe de presse
        Image qr;
        try {
            qr = Image.getInstance(java.util.Base64.getDecoder().decode(qrBase64));
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("QR code invalide");
        }
        qr.setAbsolutePosition(400, 60);
        qr.scaleToFit(120, 120);
        document.add(qr);

        // Pied : numéro d'accréditation et saison
        Font footFont = new Font(Font.HELVETICA, 8, Font.NORMAL, new Color(0x66, 0x66, 0x66));
        Paragraph foot = new Paragraph(
                "Accreditation n° WAC-PRESSE-" + user.getId() + " · Saison " + java.time.LocalDate.now().getYear()
                + " · Document généré électroniquement — valable avec pièce d'identité.",
                footFont);
        foot.setAlignment(Element.ALIGN_CENTER);
        foot.setSpacingBefore(150);
        document.add(foot);

        document.close();
        return outputStream.toByteArray();
    }

    /**
     * Abonnement saisonnier WAC — carte A4 avec QR code d'accès au stade.
     * Valable 15 matchs à domicile de la saison.
     *
     * @return chemin de stockage du PDF (Cloudinary publicId ou local).
     *         V1 : on retourne null + on stocke les bytes en base via le
     *         service appelant si nécessaire. Le PDF complet sera uploadé
     *         sur Cloudinary en V2.
     */
    public String generateSubscriptionPdf(UserSubscription sub, User user, String qrPayload) {
        // V1 : on ne génère pas encore le PDF physique. Le QR code base64
        // est déjà stocké sur UserSubscription.qrCodeBase64, suffisant pour
        // le scan à l'entrée. On retournera un URL Cloudinary en V2.
        // (Implémentation stub — voir tâche B.12-V2)
        return "wydad://subscription/" + sub.getId();
    }
}