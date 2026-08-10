package com.wydad.digital.ticket.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfWriter;
import com.wydad.digital.ticket.model.Ticket;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;

@Service
public class TicketPdfService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

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

            // Competition
            if (ticket.getEvent().getCompetition() != null) {
                Font compFont = FontFactory.getFont(FontFactory.HELVETICA, 12);
                Paragraph comp = new Paragraph(ticket.getEvent().getCompetition(), compFont);
                comp.setAlignment(Element.ALIGN_CENTER);
                document.add(comp);
            }

            document.add(new Paragraph(" "));

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
}
