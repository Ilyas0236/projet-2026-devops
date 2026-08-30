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

    /**
     * Attestation PDF — 100% dérivée de l'abonnement saisonnier ACTIF.
     * Affiche le nom du plan (pas un MembershipLevel legacy), la saison
     * et la période de validité de l'abonnement acheté.
     */
    public byte[] generateAttestation(UserSubscription sub, User user) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4);
        PdfWriter.getInstance(document, outputStream);

        document.open();

        // Titre
        Font titleFont = new Font(Font.HELVETICA, 24, Font.BOLD, new Color(0xCC, 0x00, 0x00));
        Paragraph title = new Paragraph("ATTESTATION D'ABONNEMENT", titleFont);
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

        String planCode = sub.getPlan() != null ? sub.getPlan().getCode() : sub.getZoneCode().getCode();
        String planName = sub.getPlan() != null ? sub.getPlan().getName() : sub.getZoneCode().getDisplayName();

        document.add(new Paragraph("Attestation n° WAC-SUB-" + sub.getId(), normalFont));
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

        Paragraph planPara = new Paragraph();
        planPara.add(new Chunk("Abonnement actif : ", boldFont));
        planPara.add(new Chunk(planName + " (" + planCode + ")", normalFont));
        document.add(planPara);

        Paragraph seasonPara = new Paragraph();
        seasonPara.add(new Chunk("Saison : ", boldFont));
        seasonPara.add(new Chunk(sub.getSeason(), normalFont));
        document.add(seasonPara);

        Paragraph validPara = new Paragraph();
        validPara.add(new Chunk("Valable du ", boldFont));
        validPara.add(new Chunk(sub.getValidFrom().toLocalDate().toString()
                + " au " + sub.getValidTo().toLocalDate().toString(), normalFont));
        document.add(validPara);

        Paragraph codePara = new Paragraph();
        codePara.add(new Chunk("Code adherent : ", boldFont));
        codePara.add(new Chunk("WAC-" + user.getReferralCode(), normalFont));
        document.add(codePara);

        document.add(Chunk.NEWLINE);
        document.add(new Paragraph("Cette attestation est delivree a titre de preuve d'abonnement saisonnier au club.", normalFont));
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
     * Abonnement saisonnier WAC — carte d'accès au stade (format paysage
     * type carte bancaire 85x54mm). Contient : nom/prénom de l'adhérent,
     * code de plan, saison, validité, et QR code d'identification.
     *
     * Le PDF est régénéré à la volée par l'endpoint
     * {@code /api/auth/subscriptions/{id}/pdf} : pas de stockage Cloudinary
     * (coût RAM VM 1 Go), les bytes sont calculés en quelques ms à partir
     * des données persistées (UserSubscription.qrCodeBase64 + relations).
     *
     * @return chemin logique "subscription-{id}" (utilisé comme identifiant
     *         dans UserSubscription.pdfPath pour audit)
     */
    public String generateSubscriptionPdf(UserSubscription sub, User user, String qrPayload) {
        // Signature historique : retourne un identifiant logique. Le PDF
        // physique est généré par {@link #buildSubscriptionPdfBytes} au
        // moment du téléchargement (cf. SubscriptionController).
        return "subscription-" + sub.getId();
    }

    /**
     * Génère les bytes PDF physiques de la carte d'abonnement à partir
     * d'un UserSubscription persisté (donc avec qrCodeBase64 déjà en base).
     * Utilisé par l'endpoint de téléchargement public-by-owner.
     */
    public byte[] buildSubscriptionPdfBytes(UserSubscription sub, User user) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        // Format paysage carte bancaire (85x54mm = 240x153pt)
        Document document = new Document(new RectangleReadOnly(360, 540));
        PdfWriter.getInstance(document, outputStream);
        document.open();

        // Bande rouge supérieure — identité visuelle du club
        Rectangle band = new Rectangle(0, 480, 360, 540);
        band.setBackgroundColor(new Color(0xCC, 0x00, 0x00));
        document.add(band);

        Font clubFont = new Font(Font.HELVETICA, 14, Font.BOLD, Color.WHITE);
        Paragraph club = new Paragraph("WYDAD ATHLETIC CLUB", clubFont);
        club.setAlignment(Element.ALIGN_CENTER);
        document.add(club);

        Font subFont = new Font(Font.HELVETICA, 8, Font.NORMAL, Color.WHITE);
        Paragraph subTitle = new Paragraph("CARTE ABONNEMENT SAISONNIER", subFont);
        subTitle.setAlignment(Element.ALIGN_CENTER);
        subTitle.setSpacingBefore(2);
        document.add(subTitle);

        document.add(Chunk.NEWLINE);

        // Saison et plan
        String planLabel = sub.getPlan() != null
                ? sub.getPlan().getName() + " (" + sub.getPlan().getCode() + ")"
                : (sub.getZoneCode() != null ? sub.getZoneCode().getDisplayName() : "—");
        Font labelFont = new Font(Font.HELVETICA, 7, Font.BOLD, new Color(0x88, 0x88, 0x88));
        Font valueFont = new Font(Font.HELVETICA, 11, Font.BOLD, new Color(0x1A, 0x1A, 0x2E));

        Paragraph planLabelP = new Paragraph("FORMULE", labelFont);
        planLabelP.setIndentationLeft(20);
        document.add(planLabelP);
        Paragraph planValueP = new Paragraph(planLabel, valueFont);
        planValueP.setIndentationLeft(20);
        planValueP.setSpacingAfter(8);
        document.add(planValueP);

        Paragraph nomLabel = new Paragraph("ADHÉRENT", labelFont);
        nomLabel.setIndentationLeft(20);
        document.add(nomLabel);
        Paragraph nom = new Paragraph(user.getFirstName() + " " + user.getLastName(), valueFont);
        nom.setIndentationLeft(20);
        nom.setSpacingAfter(8);
        document.add(nom);

        Paragraph idLabel = new Paragraph("N° ADHÉRENT", labelFont);
        idLabel.setIndentationLeft(20);
        document.add(idLabel);
        Paragraph idValue = new Paragraph(
                "WAC-" + (user.getReferralCode() != null ? user.getReferralCode() : String.valueOf(user.getId())),
                new Font(Font.HELVETICA, 11, Font.BOLD, new Color(0xCC, 0x00, 0x00)));
        idValue.setIndentationLeft(20);
        idValue.setSpacingAfter(8);
        document.add(idValue);

        Paragraph seasonLabel = new Paragraph("SAISON", labelFont);
        seasonLabel.setIndentationLeft(20);
        document.add(seasonLabel);
        Paragraph seasonValue = new Paragraph(sub.getSeason(), valueFont);
        seasonValue.setIndentationLeft(20);
        seasonValue.setSpacingAfter(4);
        document.add(seasonValue);

        Paragraph validLabel = new Paragraph("VALIDITÉ", labelFont);
        validLabel.setIndentationLeft(20);
        document.add(validLabel);
        Paragraph validValue = new Paragraph(
                sub.getValidFrom().toLocalDate() + " → " + sub.getValidTo().toLocalDate(),
                new Font(Font.HELVETICA, 9, Font.NORMAL, new Color(0x1A, 0x1A, 0x2E)));
        validValue.setIndentationLeft(20);
        document.add(validValue);

        // QR code à droite : contient l'identité complète (cf. buildQrPayload)
        if (sub.getQrCodeBase64() != null && !sub.getQrCodeBase64().isBlank()) {
            try {
                Image qr = Image.getInstance(java.util.Base64.getDecoder().decode(sub.getQrCodeBase64()));
                qr.setAbsolutePosition(240, 80);
                qr.scaleToFit(100, 100);
                document.add(qr);
            } catch (IllegalArgumentException e) {
                // QR corrompu : on continue sans, l'identifiant texte reste valide
            }
        }

        // Pied : référence interne
        Font footFont = new Font(Font.HELVETICA, 6, Font.NORMAL, new Color(0x99, 0x99, 0x99));
        Paragraph foot = new Paragraph(
                "Carte n° WAC-SUB-" + sub.getId() + " · Document généré électroniquement — "
                + "à présenter avec une pièce d'identité à l'entrée du stade.",
                footFont);
        foot.setAlignment(Element.ALIGN_CENTER);
        foot.setSpacingBefore(40);
        document.add(foot);

        document.close();
        return outputStream.toByteArray();
    }

    /**
     * V2.2 — Facture PDF d'un abonnement saisonnier (vue "comptable" :
     * A4, émetteur, ligne unique de prestation, total, mentions légales).
     * Distincte de la carte d'abonnement (format carte bancaire 85x54mm).
     */
    public byte[] buildSubscriptionInvoicePdfBytes(UserSubscription sub, User user) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4);
        PdfWriter.getInstance(document, outputStream);
        document.open();

        Font titleFont = new Font(Font.HELVETICA, 22, Font.BOLD, new Color(0xCC, 0x00, 0x00));
        Paragraph club = new Paragraph("WYDAD ATHLETIC CLUB", titleFont);
        club.setAlignment(Element.ALIGN_CENTER);
        document.add(club);

        Font subTitle = new Font(Font.HELVETICA, 14, Font.BOLD);
        Paragraph invoiceTitle = new Paragraph("FACTURE", subTitle);
        invoiceTitle.setAlignment(Element.ALIGN_CENTER);
        document.add(invoiceTitle);

        document.add(Chunk.NEWLINE);

        Font label = new Font(Font.HELVETICA, 10, Font.BOLD);
        Font value = new Font(Font.HELVETICA, 10, Font.NORMAL);

        // Émetteur
        document.add(new Paragraph("Wydad Athletic Club", label));
        document.add(new Paragraph("Stade Mohammed V, Casablanca", value));
        document.add(new Paragraph("Association — Maroc", value));
        document.add(Chunk.NEWLINE);

        // Bloc facture
        document.add(new Paragraph("Facture n° : WAC-SUB-" + sub.getId(), label));
        document.add(new Paragraph("Date : " + sub.getPaidAt().toLocalDate(), value));
        document.add(new Paragraph("Saison : " + sub.getSeason(), value));
        document.add(new Paragraph("Adhérent : " + user.getFirstName() + " " + user.getLastName()
                + " (ID " + user.getId() + ")", value));
        document.add(new Paragraph("Email : " + user.getEmail(), value));
        document.add(Chunk.NEWLINE);

        // Désignation
        String planName = sub.getPlan() != null ? sub.getPlan().getName() : sub.getZoneCode().getDisplayName();
        String planCode = sub.getPlan() != null ? sub.getPlan().getCode() : sub.getZoneCode().getCode();
        document.add(new Paragraph("Abonnement saisonnier " + planName + " (" + planCode + ")", value));
        document.add(new Paragraph("Validité : " + sub.getValidFrom().toLocalDate()
                + " → " + sub.getValidTo().toLocalDate(), value));
        document.add(new Paragraph("Référence paiement : " + sub.getTransactionRef(), value));
        document.add(Chunk.NEWLINE);

        // Total
        Font totalFont = new Font(Font.HELVETICA, 12, Font.BOLD);
        Paragraph total = new Paragraph("TOTAL TTC : " + sub.getPaidAmount() + " MAD", totalFont);
        total.setAlignment(Element.ALIGN_RIGHT);
        document.add(total);
        document.add(new Paragraph("TVA non applicable — association loi 1901 / Maroc.", value));
        document.add(Chunk.NEWLINE);

        // Mentions
        Font foot = new Font(Font.HELVETICA, 8, Font.NORMAL, new Color(0x99, 0x99, 0x99));
        Paragraph mention = new Paragraph(
                "Cette facture est générée électroniquement. Conservez-la comme justificatif "
                + "de paiement. Référence interne : WAC-SUB-" + sub.getId() + ".",
                foot);
        mention.setAlignment(Element.ALIGN_CENTER);
        document.add(mention);

        document.close();
        return outputStream.toByteArray();
    }
}