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
