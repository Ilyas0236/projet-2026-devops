package com.wydad.digital.ticket.service;

import com.wydad.digital.ticket.client.NotificationClient;
import com.wydad.digital.ticket.client.PaymentClient;
import com.wydad.digital.ticket.dto.PurchaseTicketRequest;
import com.wydad.digital.ticket.dto.TicketResponse;
import com.wydad.digital.ticket.enums.TicketCategory;
import com.wydad.digital.ticket.filter.UserContext;
import com.wydad.digital.ticket.model.Event;
import com.wydad.digital.ticket.model.Section;
import com.wydad.digital.ticket.repository.EventRepository;
import com.wydad.digital.ticket.repository.SectionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * B.1 — Tests metier de la billetterie :
 * 1. le prix du billet est TOUJOURS celui serveur de la section, pas une
 *    valeur envoyee par le client ;
 * 2. la double validation d'un billet deja USED est rejetee ;
 * 3. l'annulation rembourse le wallet et restaure les places ;
 * 4. deux achats concurrents sur la derniere place : exactement un reussit
 *    (verrou pessimiste sur l'evenement).
 *
 * H2 en mode compatibilite PostgreSQL : supporte SELECT ... FOR UPDATE,
 * mecanisme exact du verrou pessimiste. Comportement a revalider en
 * integration contre le PostgreSQL reel (docker-compose).
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:tickettest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        // L'application.yml force le dialecte PostgreSQL : on le remplace pour H2.
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TicketServiceTest {

    private static final Long USER_ID = 42L;
    private static final String USER_EMAIL = "fan@wydad.ma";

    @Autowired
    private TicketService ticketService;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private SectionRepository sectionRepository;

    /** Aucun appel HTTP reel : le paiement est simule accepte. */
    @MockBean
    private PaymentClient paymentClient;

    @MockBean
    private NotificationClient notificationClient;

    private Long eventId;
    private Long sectionId;

    @BeforeAll
    void seedEventWithSection() {
        when(paymentClient.refundEcash(anyString(), any(), anyString())).thenReturn(true);

        Event event = eventRepository.save(Event.builder()
                .title("WAC - Raja (test)")
                .eventType(com.wydad.digital.ticket.enums.EventType.FOOTBALL)
                .venue("Complexe Mohammed V")
                .eventDate(LocalDateTime.now().plusDays(7))
                .basePrice(new BigDecimal("100.00"))
                .totalCapacity(100)
                .availableSeats(100)
                .soldTickets(0)
                .build());

        Section section = sectionRepository.save(Section.builder()
                .name("Virage Nord (test)")
                .category(TicketCategory.VIRAGE_NORD)
                .capacity(100)
                .availableSeats(100)
                .price(new BigDecimal("120.00"))
                .event(event)
                .build());

        this.eventId = event.getId();
        this.sectionId = section.getId();
    }

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    private List<TicketResponse> purchaseAsFan(int qty) {
        UserContext.setCurrentUserId(USER_ID);
        UserContext.setCurrentUserEmail(USER_EMAIL);
        UserContext.setCurrentUserRole("ADHERENT");
        PurchaseTicketRequest request = new PurchaseTicketRequest();
        request.setEventId(eventId);
        request.setUserId(USER_ID);
        request.setUserFullName("Fan Test");
        request.setUserEmail(USER_EMAIL);
        request.setCategory(TicketCategory.VIRAGE_NORD);
        request.setQuantity(qty);
        return ticketService.purchaseTickets(request);
    }

    /**
     * Le client ne fournit aucun champ prix dans PurchaseTicketRequest :
     * le billet porte obligatoirement le prix serveur de la section (120 DH),
     * meme si l'event basePrice est different (100 DH).
     */
    @Test
    void prixDuBilletEstToujoursLePrixServerDeLaSection() {
        // Assertion relative : les autres tests de la classed partagent la
        // meme section et achetent eux aussi des places.
        int placesAvant = sectionRepository.findById(sectionId).orElseThrow().getAvailableSeats();

        List<TicketResponse> tickets = purchaseAsFan(2);

        assertEquals(2, tickets.size());
        for (TicketResponse t : tickets) {
            assertEquals(0, new BigDecimal("120.00").compareTo(t.getPrice()),
                    "Prix = prix serveur de la section, jamais une valeur client");
            assertEquals(eventId, t.getEventId());
        }
        Section section = sectionRepository.findById(sectionId).orElseThrow();
        assertEquals(placesAvant - 2, section.getAvailableSeats(),
                "Places decrementees exactement de la quantite achetee");
    }

    @Test
    void doubleValidationDUnBilletDejaUtiliseEstRejetee() {
        TicketResponse bought = purchaseAsFan(1).get(0);
        UserContext.setCurrentUserRole("STAFF");

        ticketService.validateTicket(bought.getQrCodeData());

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> ticketService.validateTicket(bought.getQrCodeData()));
        assertTrue(ex.getMessage().contains("déjà été utilisé"),
                "Second scan d'un billet USED doit etre rejete");
    }

    @Test
    void annulationRembourseLeWalletEtRestaureLesPlaces() {
        int placesAvant = sectionRepository.findById(sectionId).orElseThrow().getAvailableSeats();
        TicketResponse bought = purchaseAsFan(1).get(0);

        UserContext.setCurrentUserId(USER_ID);
        UserContext.setCurrentUserEmail(USER_EMAIL);
        UserContext.setCurrentUserRole("ADHERENT");
        TicketResponse cancelled = ticketService.cancelTicket(bought.getId());

        assertEquals("REFUNDED", cancelled.getStatus().name(),
                "Billet rembourse quand payment-service confirme le refund");
        int placesApres = sectionRepository.findById(sectionId).orElseThrow().getAvailableSeats();
        assertEquals(placesAvant, placesApres, "Place restituee apres annulation");
    }

    /** Deux achats concurrents sur une seule place restante : exactement un reussit. */
    @Test
    void deuxAchatsConcurrentsSurLaDernierePlaceNeVendentPasDeuxBillets() throws Exception {
        Section section = sectionRepository.findById(sectionId).orElseThrow();
        section.setAvailableSeats(1);
        sectionRepository.save(section);

        CountDownLatch startGate = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> f1 = pool.submit(() -> tryPurchase(startGate));
            Future<Boolean> f2 = pool.submit(() -> tryPurchase(startGate));

            startGate.countDown();
            boolean oneSucceeded = f1.get(30, TimeUnit.SECONDS) ^ f2.get(30, TimeUnit.SECONDS);

            assertTrue(oneSucceeded, "Exactement un des deux achats concurrents doit reussir");
            assertEquals(0, sectionRepository.findById(sectionId).orElseThrow().getAvailableSeats(),
                    "La derniere place est vendue une seule fois");
        } finally {
            pool.shutdownNow();
            section.setAvailableSeats(Math.max(section.getAvailableSeats() - 1, 0));
            sectionRepository.save(section);
        }
    }

    private Boolean tryPurchase(CountDownLatch startGate) {
        try {
            startGate.await();
            purchaseAsFan(1);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
