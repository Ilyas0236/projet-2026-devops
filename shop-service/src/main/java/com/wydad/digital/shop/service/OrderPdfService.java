package com.wydad.digital.shop.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfWriter;
import com.wydad.digital.shop.model.OrderItem;
import com.wydad.digital.shop.model.ShopOrder;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;

/**
 * V2.2 — Facture PDF pour une commande boutique.
 *
 * Vue "comptable" distincte du ticket d'achat (pas de QR, pas de
 * bordereau de livraison) : émetteur, numéro de commande, lignes de
 * facturation (article × PU × qté), sous-total, remises, livraison,
 * total TTC, mentions.
 */
@Service
public class OrderPdfService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public byte[] generateInvoicePdf(ShopOrder order) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, baos);
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
            document.add(new Paragraph("Boutique officielle — Stade Mohammed V, Casablanca", value));
            document.add(new Paragraph("Maroc", value));
            document.add(Chunk.NEWLINE);

            // Bloc client
            document.add(new Paragraph("Facture n° : " + order.getOrderNumber(), label));
            document.add(new Paragraph("Date : "
                    + (order.getCreatedAt() != null ? order.getCreatedAt().format(DATE_FMT) : "—"), value));
            document.add(new Paragraph("Client : " + order.getUserEmail(), value));
            if (order.getShippingAddress() != null) {
                StringBuilder addr = new StringBuilder(order.getShippingAddress());
                if (order.getShippingCity() != null) addr.append(", ").append(order.getShippingCity());
                document.add(new Paragraph("Adresse : " + addr, value));
            }
            if (Boolean.TRUE.equals(order.getClickAndCollect())
                    && order.getPickupStore() != null && order.getPickupStore().getName() != null) {
                document.add(new Paragraph("Retrait en magasin : " + order.getPickupStore().getName(), value));
            }
            document.add(Chunk.NEWLINE);

            // Tableau de lignes
            Table itemsTable = new Table(4);
            itemsTable.setWidth(100);
            itemsTable.setBorderWidth(1);
            itemsTable.getDefaultCell().setBorder(1);

            Font headerFont = new Font(Font.HELVETICA, 10, Font.BOLD, new Color(255, 255, 255));
            Color headerBg = new Color(0xCC, 0x00, 0x00);

            Cell h1 = new Cell(new Paragraph("Article", headerFont));
            h1.setBackgroundColor(headerBg);
            itemsTable.addCell(h1);
            Cell h2 = new Cell(new Paragraph("Quantité", headerFont));
            h2.setBackgroundColor(headerBg);
            itemsTable.addCell(h2);
            Cell h3 = new Cell(new Paragraph("PU (MAD)", headerFont));
            h3.setBackgroundColor(headerBg);
            itemsTable.addCell(h3);
            Cell h4 = new Cell(new Paragraph("Total (MAD)", headerFont));
            h4.setBackgroundColor(headerBg);
            itemsTable.addCell(h4);

            for (OrderItem item : order.getItems()) {
                String design = item.getProductName();
                if (item.getVariantInfo() != null) {
                    design += " (" + item.getVariantInfo() + ")";
                }
                itemsTable.addCell(new Cell(new Paragraph(design, value)));
                itemsTable.addCell(new Cell(new Paragraph(String.valueOf(item.getQuantity()), value)));
                itemsTable.addCell(new Cell(new Paragraph(String.valueOf(item.getUnitPrice()), value)));
                itemsTable.addCell(new Cell(new Paragraph(String.valueOf(item.getTotalPrice()), value)));
            }
            document.add(itemsTable);

            document.add(Chunk.NEWLINE);

            // Bloc totaux aligné à droite
            Font bold = new Font(Font.HELVETICA, 11, Font.BOLD);
            if (order.getSubtotal() != null) {
                addRightLine(document, value, "Sous-total : " + order.getSubtotal() + " MAD");
            }
            if (order.getDiscountAmount() != null && order.getDiscountAmount().signum() > 0) {
                addRightLine(document, value, "Remise promo (-" + order.getDiscountAmount() + " MAD)");
            }
            if (order.getAdherentDiscount() != null && order.getAdherentDiscount().signum() > 0) {
                addRightLine(document, value, "Remise adhérent (-" + order.getAdherentDiscount() + " MAD)");
            }
            if (order.getShippingCost() != null) {
                addRightLine(document, value, "Livraison : " + order.getShippingCost() + " MAD");
            }
            Paragraph total = new Paragraph("TOTAL TTC : " + order.getTotalAmount() + " MAD", bold);
            total.setAlignment(Element.ALIGN_RIGHT);
            document.add(total);

            document.add(Chunk.NEWLINE);
            document.add(new Paragraph("Mode de paiement : " + order.getPaymentStatus(), value));
            document.add(Chunk.NEWLINE);

            Font foot = new Font(Font.HELVETICA, 8, Font.NORMAL, new Color(0x99, 0x99, 0x99));
            Paragraph mention = new Paragraph(
                    "Cette facture est générée électroniquement. Conservez-la comme justificatif "
                    + "de paiement. Commande n° " + order.getOrderNumber() + ".",
                    foot);
            mention.setAlignment(Element.ALIGN_CENTER);
            document.add(mention);

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Erreur lors de la génération de la facture PDF", e);
        }
    }

    private void addRightLine(Document doc, Font font, String text) throws DocumentException {
        Paragraph p = new Paragraph(text, font);
        p.setAlignment(Element.ALIGN_RIGHT);
        doc.add(p);
    }
}
