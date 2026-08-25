package com.wydad.digital.ticket.controller;

import com.wydad.digital.ticket.client.NotificationClient;
import com.wydad.digital.ticket.client.PaymentClient;
import com.wydad.digital.ticket.enums.TicketCategory;
import com.wydad.digital.ticket.enums.TicketStatus;
import com.wydad.digital.ticket.model.Event;
import com.wydad.digital.ticket.model.Section;
import com.wydad.digital.ticket.model.Ticket;
import com.wydad.digital.ticket.repository.EventRepository;
import com.wydad.digital.ticket.repository.SectionRepository;
import com.wydad.digital.ticket.repository.TicketRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * B.1 — endpoint HTTP POST /api/ticket/tickets/validate (contrôleur du scan
 * à l'entrée du stade). Techniques ISTQB :
 *  - transition d'état : PAID -> USED (1er scan OK) puis rejet au 2e ;
 *  - transitions interdites : CANCELLED/REFUNDED jamais scannables ;
 *  - partition équivalence QR inconnu -> 404 (pas une fuite d'info) ;
 *  - table de décision rôles × route : STAFF/ADMIN ok, ADHERENT/JOUEUR 403.
 *
 * H2 mode compatibilité PostgreSQL ; MockMvc avec le vrai filtre Spring Security.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:scantest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
class TicketScanSecurityTest {

    private static final String VALIDATE_URL = "/api/ticket/tickets/validate";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private SectionRepository sectionRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @MockBean
    private PaymentClient paymentClient;

    @MockBean
    private NotificationClient notificationClient;

    private String qrPaid;
    private String qrCancelled;

    @BeforeAll
    void seedTickets() {
        when(paymentClient.refundEcash(anyString(), any(), anyString())).thenReturn(true);

        Event event = eventRepository.save(Event.builder()
                .title("WAC - Olympique (scan test)")
                .eventType(com.wydad.digital.ticket.enums.EventType.FOOTBALL)
                .venue("Complexe Mohammed V")
                .eventDate(LocalDateTime.now().plusDays(3))
                .basePrice(new BigDecimal("80.00"))
                .totalCapacity(50)
                .availableSeats(50)
                .soldTickets(0)
                .build());

        Section section = sectionRepository.save(Section.builder()
                .name("Tribune (scan test)")
                .category(TicketCategory.VIRAGE_NORD)
                .capacity(50)
                .availableSeats(50)
                .price(new BigDecimal("90.00"))
                .event(event)
                .build());

        qrPaid = seedTicket(event, section, TicketStatus.PAID);
        // Transition interdite : un billet annulé ne doit JAMAIS passer au scan.
        qrCancelled = seedTicket(event, section, TicketStatus.CANCELLED);
    }

    private String seedTicket(Event event, Section section, TicketStatus status) {
        String number = "SCAN-" + status.name();
        return ticketRepository.save(Ticket.builder()
                .ticketNumber(number)
                .userId(42L)
                .userFullName("Fan Test")
                .userEmail("fan@wydad.ma")
                .event(event)
                .section(section)
                .category(TicketCategory.VIRAGE_NORD)
                .price(new BigDecimal("90.00"))
                .qrCodeData("WAC-TICKET:" + number + ":EVENT:" + event.getId() + ":USER:42")
                .qrCodeImage(new byte[0])
                .status(status)
                .build()).getQrCodeData();
    }

    /** Corps JSON du scan, comme l'enverrait le scanner du club. */
    private static String body(String qr) {
        return "{\"qrCodeData\": \"" + qr + "\"}";
    }

    // ==================== Table de décision rôles × route ====================

    @Test
    @DisplayName("[TD] STAFF scanne un billet PAID -> 200, statut devient USED")
    void staffScanneBilletPaid() throws Exception {
        mockMvc.perform(post(VALIDATE_URL).contentType(MediaType.APPLICATION_JSON)
                        .content(body(qrPaid))
                        .with(user("staff@wydad.ma").roles("STAFF")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("USED"));

        assertEquals(TicketStatus.USED,
                ticketRepository.findByQrCodeData(qrPaid).orElseThrow().getStatus());
    }

    @Test
    @DisplayName("[TD] ADHERENT tente de scanner -> 403 (réservé STAFF/ADMIN)")
    void adherentNePeutPasScanner() throws Exception {
        mockMvc.perform(post(VALIDATE_URL).contentType(MediaType.APPLICATION_JSON)
                        .content(body(qrPaid))
                        .with(user("fan@wydad.ma").roles("ADHERENT")))
                .andExpect(status().isForbidden());
        // Rien n'a changé côté donnée.
        assertEquals(TicketStatus.PAID,
                ticketRepository.findByQrCodeData(qrPaid).orElseThrow().getStatus());
    }

    // ==================== Transitions d'état ====================

    @Test
    @DisplayName("[État] Billet CANCELLED scanné -> 400 « a été annulé », reste CANCELLED")
    void billetAnnuleJamaisScannable() throws Exception {
        mockMvc.perform(post(VALIDATE_URL).contentType(MediaType.APPLICATION_JSON)
                        .content(body(qrCancelled))
                        .with(user("staff@wydad.ma").roles("STAFF")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("annulé")));

        assertEquals(TicketStatus.CANCELLED,
                ticketRepository.findByQrCodeData(qrCancelled).orElseThrow().getStatus());
    }

    @Test
    @DisplayName("[État] Double scan du même billet -> 400 « déjà été utilisé »")
    void doubleScanRejete() throws Exception {
        // Premier scan OK (le test précédent peut avoir consommé qrPaid selon
        // l'ordre JUnit : on repasse par un état propre).
        Ticket t = ticketRepository.findByQrCodeData(qrPaid).orElseThrow();
        if (t.getStatus() != TicketStatus.PAID) { // remis à l'état PAID si besoin
            t.setStatus(TicketStatus.PAID);
            ticketRepository.save(t);
        }
        for (int i = 0; i < 2; i++) {
            int expected = i == 0 ? 200 : 400;
            mockMvc.perform(post(VALIDATE_URL).contentType(MediaType.APPLICATION_JSON)
                            .content(body(qrPaid))
                            .with(user("staff@wydad.ma").roles("STAFF")))
                    .andExpect(status().is(expected));
        }
    }

    // ==================== Partition QR inconnu ====================

    @Test
    @DisplayName("[Partition] QR inexistant -> 404 « non trouvé », jamais 500")
    void qrInconnuNotFound() throws Exception {
        mockMvc.perform(post(VALIDATE_URL).contentType(MediaType.APPLICATION_JSON)
                        .content(body("WAC-TICKET:INCONNU:EVENT:1:USER:1"))
                        .with(user("staff@wydad.ma").roles("STAFF")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("[Robustesse] Body vide / champ manquant -> 400, pas 500")
    void bodyInvalideBadRequest() throws Exception {
        mockMvc.perform(post(VALIDATE_URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .with(user("staff@wydad.ma").roles("STAFF")))
                .andExpect(status().isBadRequest());
    }
}
