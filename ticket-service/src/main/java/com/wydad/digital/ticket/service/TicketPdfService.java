package com.wydad.digital.ticket.service;

import com.lowagie.text.*;
import com.lowagie.text.alignment.HorizontalAlignment;
import com.lowagie.text.alignment.VerticalAlignment;
import com.lowagie.text.pdf.PdfWriter;
import com.wydad.digital.ticket.model.Ticket;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.time.format.DateTimeFormatter;

@Service
public class TicketPdfService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    /** Logo du club empaqueté dans le jar (ressource classpath). */
    private static final String WYDAD_LOGO_RESOURCE = "/logos/wydad-logo.png";

    public byte[] generateTicketPdf(Ticket ticket) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Document document = new Document(PageSize.A5.rotate());
            PdfWriter.getInstance(document, baos);
            document.open();

            // Header - Club name
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, new java.awt.Color(190, 30, 45));
            Paragraph clubName = new Paragraph("WYDAD ATHLETIC CLUB", titleFont);
            clubName.setAlignment(Element.ALIGN_CENTER);
            document.add(clubName);

            document.add(new Paragraph(" "));

            // Event Title
            Font eventFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
            String eventTitle = ticket.getEvent().getHomeTeam() + " vs " + ticket.getEvent().getAwayTeam();
            Paragraph eventParagraph = new Paragraph(eventTitle, eventFont);
            eventParagraph.setAlignment(Element.ALIGN_CENTER);
            document.add(eventParagraph);

            // §26 — discipline + catégorie du groupe concerné
            Font groupFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, new java.awt.Color(190, 30, 45));
            String groupe = ticket.getEvent().getEventType().name()
                    + (ticket.getEvent().getCategory() != null ? " · " + ticket.getEvent().getCategory().name() : "");
            Paragraph groupParagraph = new Paragraph(groupe, groupFont);
            groupParagraph.setAlignment(Element.ALIGN_CENTER);
            document.add(groupParagraph);

            // Competition
            if (ticket.getEvent().getCompetition() != null) {
                Font compFont = FontFactory.getFont(FontFactory.HELVETICA, 12);
                Paragraph comp = new Paragraph(ticket.getEvent().getCompetition(), compFont);
                comp.setAlignment(Element.ALIGN_CENTER);
                document.add(comp);
            }

            document.add(new Paragraph(" "));

            // Logos des deux équipes côte à côte (§16/§21) : le Wydad depuis
            // la ressource du jar, l'adversaire depuis l'URL téléversée par
            // l'ADMIN. Best-effort : sans logo disponible on n'échoue jamais.
            Image wydadLogo = loadClasspathImage(WYDAD_LOGO_RESOURCE);
            Image adversaireLogo = loadUrlImage(ticket.getEvent().getAdversaireLogoUrl());
            if (wydadLogo != null || adversaireLogo != null) {
                Table logosTable = new Table(3);
                logosTable.setWidth(100);
                logosTable.setBorderWidth(0);
                logosTable.getDefaultCell().setBorder(Rectangle.NO_BORDER);
                logosTable.getDefaultCell().setHorizontalAlignment(HorizontalAlignment.CENTER);

                Cell homeCell = new Cell();
                homeCell.setBorder(Rectangle.NO_BORDER);
                homeCell.setHorizontalAlignment(HorizontalAlignment.RIGHT);
                if (wydadLogo != null) {
                    Image scaledHome = Image.getInstance(wydadLogo);
                    scaledHome.scaleToFit(60, 60);
                    homeCell.addElement(scaledHome);
                }
                logosTable.addCell(homeCell);

                Cell vsCell = new Cell("VS");
                vsCell.setBorder(Rectangle.NO_BORDER);
                vsCell.setHorizontalAlignment(HorizontalAlignment.CENTER);
                vsCell.setVerticalAlignment(VerticalAlignment.CENTER);
                logosTable.addCell(vsCell);

                Cell awayCell = new Cell();
                awayCell.setBorder(Rectangle.NO_BORDER);
                awayCell.setHorizontalAlignment(HorizontalAlignment.LEFT);
                if (adversaireLogo != null) {
                    adversaireLogo.scaleToFit(60, 60);
                    awayCell.addElement(adversaireLogo);
                }
                logosTable.addCell(awayCell);

                document.add(logosTable);
                document.add(new Paragraph(" "));
            }

            // Details
            Font detailFont = FontFactory.getFont(FontFactory.HELVETICA, 11);
            document.add(new Paragraph("Date: " + ticket.getEvent().getEventDate().format(DATE_FMT), detailFont));
            document.add(new Paragraph("Stade: " + ticket.getEvent().getVenue(), detailFont));
            document.add(new Paragraph("Tribune: " + ticket.getCategory().name(), detailFont));
            if (ticket.getSeatNumber() != null) {
                document.add(new Paragraph("Place: " + ticket.getSeatNumber(), detailFont));
            }
            document.add(new Paragraph("Prix: " + ticket.getPrice() + " MAD", detailFont));

            document.add(new Paragraph(" "));

            // Ticket info
            Font ticketFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
            document.add(new Paragraph("N° Billet: " + ticket.getTicketNumber(), ticketFont));
            document.add(new Paragraph("Nom: " + ticket.getUserFullName(), ticketFont));

            document.add(new Paragraph(" "));

            // QR Code
            if (ticket.getQrCodeImage() != null) {
                Image qrImage = Image.getInstance(ticket.getQrCodeImage());
                qrImage.scaleToFit(120, 120);
                qrImage.setAlignment(Element.ALIGN_CENTER);
                document.add(qrImage);
            }

            // Footer
            Font footerFont = FontFactory.getFont(FontFactory.HELVETICA, 8, java.awt.Color.GRAY);
            Paragraph footer = new Paragraph("Ce billet est personnel et non transférable. Présentez le QR code à l'entrée.", footerFont);
            footer.setAlignment(Element.ALIGN_CENTER);
            document.add(footer);

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Erreur lors de la génération du PDF du billet", e);
        }
    }

    /**
     * V2.2 — Facture PDF d'un billet (vue "comptable" différente du
     * billet d'entrée avec QR). Mêmes données de l'événement + ligne
     * de facturation (nombre, prix unitaire, total, TVA), numéro de
     * facture = numéro du billet (référence unique côté billetterie).
     */
    public byte[] generateInvoicePdf(Ticket ticket) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, baos);
            document.open();

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, new java.awt.Color(190, 30, 45));
            Paragraph clubName = new Paragraph("WYDAD ATHLETIC CLUB", titleFont);
            clubName.setAlignment(Element.ALIGN_CENTER);
            document.add(clubName);

            Font subTitle = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);
            Paragraph invoiceTitle = new Paragraph("FACTURE", subTitle);
            invoiceTitle.setAlignment(Element.ALIGN_CENTER);
            document.add(invoiceTitle);

            document.add(new Paragraph(" "));

            Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
            Font valueFont = FontFactory.getFont(FontFactory.HELVETICA, 10);

            // Bloc émetteur
            document.add(new Paragraph("Wydad Athletic Club", labelFont));
            document.add(new Paragraph("Stade Mohammed V, Casablanca", valueFont));
            document.add(new Paragraph("SIRET / IF : 000000000000000 - Maroc", valueFont));
            document.add(new Paragraph(" "));

            // Bloc client + facture
            document.add(new Paragraph("Facture n° : BILL-" + ticket.getTicketNumber(), labelFont));
            document.add(new Paragraph("Date : " + ticket.getCreatedAt().format(DATE_FMT), valueFont));
            document.add(new Paragraph("Événement : " + ticket.getEvent().getHomeTeam()
                    + " vs " + ticket.getEvent().getAwayTeam(), valueFont));
            document.add(new Paragraph("Date événement : "
                    + ticket.getEvent().getEventDate().format(DATE_FMT), valueFont));
            document.add(new Paragraph("Lieu : " + ticket.getEvent().getVenue(), valueFont));
            if (ticket.getEvent().getCompetition() != null) {
                document.add(new Paragraph("Compétition : "
                        + ticket.getEvent().getCompetition(), valueFont));
            }
            document.add(new Paragraph("Acheteur : " + ticket.getUserFullName(), valueFont));
            document.add(new Paragraph(" "));

            // Tableau de la ligne de facturation
            Table itemsTable = new Table(4);
            itemsTable.setWidth(100);
            itemsTable.setBorderWidth(1);
            itemsTable.getDefaultCell().setBorder(1);

            Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, new java.awt.Color(255, 255, 255));
            java.awt.Color headerBg = new java.awt.Color(190, 30, 45);

            Cell h1 = new Cell(new Paragraph("Désignation", headerFont));
            h1.setBackgroundColor(headerBg);
            h1.setHeader(true);
            itemsTable.addCell(h1);
            Cell h2 = new Cell(new Paragraph("Quantité", headerFont));
            h2.setBackgroundColor(headerBg);
            h2.setHeader(true);
            itemsTable.addCell(h2);
            Cell h3 = new Cell(new Paragraph("PU (MAD)", headerFont));
            h3.setBackgroundColor(headerBg);
            h3.setHeader(true);
            itemsTable.addCell(h3);
            Cell h4 = new Cell(new Paragraph("Total (MAD)", headerFont));
            h4.setBackgroundColor(headerBg);
            h4.setHeader(true);
            itemsTable.addCell(h4);

            Cell design = new Cell(new Paragraph(
                    "Billet match — Tribune " + ticket.getCategory().name()
                    + (ticket.getSeatNumber() != null ? " — Place " + ticket.getSeatNumber() : ""),
                    valueFont));
            itemsTable.addCell(design);
            itemsTable.addCell(new Cell(new Paragraph("1", valueFont)));
            itemsTable.addCell(new Cell(new Paragraph(String.valueOf(ticket.getPrice()), valueFont)));
            itemsTable.addCell(new Cell(new Paragraph(String.valueOf(ticket.getPrice()), valueFont)));
            document.add(itemsTable);

            document.add(new Paragraph(" "));

            // Bloc totaux
            Font bold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);
            Paragraph totalLine = new Paragraph("Total HT : " + ticket.getPrice() + " MAD", valueFont);
            totalLine.setAlignment(Element.ALIGN_RIGHT);
            document.add(totalLine);
            Paragraph tvaLine = new Paragraph("TVA (0% — association) : 0,00 MAD", valueFont);
            tvaLine.setAlignment(Element.ALIGN_RIGHT);
            document.add(tvaLine);
            Paragraph totalTtc = new Paragraph("TOTAL TTC : " + ticket.getPrice() + " MAD", bold);
            totalTtc.setAlignment(Element.ALIGN_RIGHT);
            document.add(totalTtc);

            document.add(new Paragraph(" "));
            document.add(new Paragraph(" "));

            Font footerFont = FontFactory.getFont(FontFactory.HELVETICA, 8, java.awt.Color.GRAY);
            Paragraph footer = new Paragraph(
                    "Cette facture est générée électroniquement par le WAC. "
                    + "Conservez-la comme justificatif de paiement. "
                    + "Référence unique : " + ticket.getTicketNumber() + ".",
                    footerFont);
            footer.setAlignment(Element.ALIGN_CENTER);
            document.add(footer);

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Erreur lors de la génération de la facture PDF", e);
        }
    }

    /** Charge une image depuis les ressources du jar ; null si absente. */
    private Image loadClasspathImage(String resource) {
        try (InputStream in = TicketPdfService.class.getResourceAsStream(resource)) {
            return in == null ? null : Image.getInstance(in.readAllBytes());
        } catch (Exception e) {
            return null;
        }
    }

    /** Télécharge le logo adverse depuis son URL média ; null si injoignable. */
    private Image loadUrlImage(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try (InputStream in = URI.create(url).toURL().openStream()) {
            return Image.getInstance(in.readAllBytes());
        } catch (Exception e) {
            return null;
        }
    }
}
