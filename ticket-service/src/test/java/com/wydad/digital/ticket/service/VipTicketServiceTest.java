package com.wydad.digital.ticket.service;

import com.wydad.digital.ticket.client.AuthClient;
import com.wydad.digital.ticket.client.NotificationClient;
import com.wydad.digital.ticket.enums.TicketCategory;
import com.wydad.digital.ticket.model.Event;
import com.wydad.digital.ticket.model.Section;
import com.wydad.digital.ticket.repository.EventRepository;
import com.wydad.digital.ticket.repository.SectionRepository;
import com.wydad.digital.ticket.repository.TicketRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Phase 2 (E) — Tests métier de la génération automatique des billets VIP :
 *
 * 1. 4 billets VIP par joueur actif, à domicile uniquement ;
 * 2. match à l'extérieur → aucun billet, rejet explicite ;
 * 3. régénération idempotente → aucun doublon (cas ISTQB « billet dupliqué ») ;
 * 4. prix 0 + statut PAID : hors circuit E-cash, jamais débité ;
 * 5. notification in-app best-effort envoyée à chaque joueur servi.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:viptest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VipTicketServiceTest {

    private static final Long JOUEUR_1_ID = 101L;
    private static final String JOUEUR_1_EMAIL = "joueur1@wydad.ma";
    private static final Long JOUEUR_2_ID = 102L;
    private static final String JOUEUR_2_EMAIL = "joueur2@wydad.ma";

    @Autowired
    private VipTicketService vipTicketService;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private SectionRepository sectionRepository;

    @MockBean
    private AuthClient authClient;

    @MockBean
    private NotificationClient notificationClient;

    private Long homeEventId;
    private Long awayEventId;
    private Long vipSectionId;

    @BeforeAll
    void seedEvents() {
        // Match à DOMICILE avec section VIP
        Event home = eventRepository.save(Event.builder()
                .title("WAC - Raja (VIP test)")
                .eventType(com.wydad.digital.ticket.enums.EventType.FOOTBALL)
                .homeTeam("Wydad AC")
                .awayTeam("Raja CA")
                .venue("Complexe Mohammed V")
                .eventDate(LocalDateTime.now().plusDays(10))
                .basePrice(new BigDecimal("100.00"))
                .totalCapacity(200)
                .availableSeats(200)
                .soldTickets(0)
                .build());

        Section vip = sectionRepository.save(Section.builder()
                .name("Tribune VIP (test)")
                .category(TicketCategory.VIP)
                .capacity(50)
                .availableSeats(50)
                .price(BigDecimal.ZERO)
                .event(home)
                .build());

        this.homeEventId = home.getId();
        this.vipSectionId = vip.getId();

        // Match à l'EXTÉRIEUR : le Wydad n'est pas recevant
        Event away = eventRepository.save(Event.builder()
                .title("FUS - Wydad (exterieur test)")
                .eventType(com.wydad.digital.ticket.enums.EventType.FOOTBALL)
                .homeTeam("FUS Rabat")
                .awayTeam("Wydad AC")
                .venue("Stade du FUS")
                .eventDate(LocalDateTime.now().plusDays(12))
                .basePrice(new BigDecimal("80.00"))
                .totalCapacity(100)
                .availableSeats(100)
                .soldTickets(0)
                .build());
        this.awayEventId = away.getId();
    }

    /**
     * Le stub des joueurs est reposé AVANT CHAQUE test : @MockBean est
     * réinitialisé après chaque méthode de test, un stub posé en @BeforeAll
     * ne survivrait qu'au premier test exécuté.
     */
    @BeforeEach
    void stubDeuxJoueursActifs() {
        org.mockito.BDDMockito.given(authClient.fetchActivePlayers()).willReturn(List.of(
                new AuthClient.PlayerRecipient(JOUEUR_1_ID, JOUEUR_1_EMAIL,
                        "Youssef", "El Amrani", "JOUEUR", "VALIDE", true),
                new AuthClient.PlayerRecipient(JOUEUR_2_ID, JOUEUR_2_EMAIL,
                        "Omar", "Naji", "JOUEUR", "VALIDE", true)));
    }

    @AfterEach
    void cleanupTickets() {
        ticketRepository.deleteAll();
        sectionRepository.findById(vipSectionId).ifPresent(s -> {
            s.setAvailableSeats(50);
            sectionRepository.save(s);
        });
    }

    // ---------- 1. Génération nominale ----------

    @Test
    void quatreBilletsVipParJoueurActifADomicile() {
        var result = vipTicketService.generateVipTicketsForEvent(homeEventId);

        assertThat(result.joueursServis()).isEqualTo(2);
        assertThat(result.billetsCrees()).isEqualTo(8);

        var billetsJ1 = ticketRepository.findByUserIdOrderByCreatedAtDesc(JOUEUR_1_ID);
        assertThat(billetsJ1).hasSize(4);
        assertThat(billetsJ1).allMatch(t -> t.getCategory() == TicketCategory.VIP);
        assertThat(billetsJ1).allMatch(t -> t.getEvent().getId().equals(homeEventId));

        var billetsJ2 = ticketRepository.findByUserIdOrderByCreatedAtDesc(JOUEUR_2_ID);
        assertThat(billetsJ2).hasSize(4);

        // Chaque billet porte un QR unique et une image non vide
        assertThat(billetsJ1).extracting("qrCodeData").doesNotHaveDuplicates();
        assertThat(billetsJ1).allMatch(t -> t.getQrCodeImage() != null && t.getQrCodeImage().length > 0);
    }

    // ---------- 2. Match extérieur ----------

    @Test
    void matchExterieurRejeteSansAucunBillet() {
        assertThatThrownBy(() -> vipTicketService.generateVipTicketsForEvent(awayEventId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("extérieur");

        assertThat(ticketRepository.findByUserIdOrderByCreatedAtDesc(JOUEUR_1_ID)).isEmpty();
        verify(authClient, never()).fetchActivePlayers();
    }

    // ---------- 3. Idempotence / billet dupliqué ----------

    @Test
    void regenerationNeCreeAucunDoublon() {
        vipTicketService.generateVipTicketsForEvent(homeEventId);
        int billetsApresPremierePasse = ticketRepository.findAll().size();

        var result = vipTicketService.generateVipTicketsForEvent(homeEventId);

        assertThat(result.billetsCrees()).isZero();
        assertThat(ticketRepository.findAll()).hasSize(billetsApresPremierePasse);

        var billetsJ1 = ticketRepository.findByUserIdOrderByCreatedAtDesc(JOUEUR_1_ID);
        assertThat(billetsJ1).hasSize(4);
        assertThat((Object) billetsJ1.stream().map(t -> t.getQrCodeData()).distinct().count())
                .isEqualTo(4L);
    }

    /** Un nouveau joueur arrivant après la première passe est servi à la relance. */
    @Test
    void relanceServeLesJoueursManquantsUniquement() {
        org.mockito.BDDMockito.given(authClient.fetchActivePlayers()).willReturn(List.of(
                new AuthClient.PlayerRecipient(JOUEUR_1_ID, JOUEUR_1_EMAIL,
                        "Youssef", "El Amrani", "JOUEUR", "VALIDE", true)));
        vipTicketService.generateVipTicketsForEvent(homeEventId);

        // Le second joueur est ajouté entre-temps
        org.mockito.BDDMockito.given(authClient.fetchActivePlayers()).willReturn(List.of(
                new AuthClient.PlayerRecipient(JOUEUR_1_ID, JOUEUR_1_EMAIL,
                        "Youssef", "El Amrani", "JOUEUR", "VALIDE", true),
                new AuthClient.PlayerRecipient(JOUEUR_2_ID, JOUEUR_2_EMAIL,
                        "Omar", "Naji", "JOUEUR", "VALIDE", true)));

        var result = vipTicketService.generateVipTicketsForEvent(homeEventId);

        assertThat(result.billetsCrees()).isEqualTo(4); // seulement pour J2
        assertThat(ticketRepository.findByUserIdOrderByCreatedAtDesc(JOUEUR_1_ID)).hasSize(4);
        assertThat(ticketRepository.findByUserIdOrderByCreatedAtDesc(JOUEUR_2_ID)).hasSize(4);
    }

    // ---------- 4. Hors circuit E-cash ----------

    @Test
    void billetsVipGratuitsEtPayesHorsCircuitDeVente() {
        vipTicketService.generateVipTicketsForEvent(homeEventId);

        var billets = ticketRepository.findByUserIdOrderByCreatedAtDesc(JOUEUR_1_ID);
        assertThat(billets).allMatch(t -> t.getPrice().compareTo(BigDecimal.ZERO) == 0);
        assertThat(billets).allMatch(t -> t.getStatus()
                == com.wydad.digital.ticket.enums.TicketStatus.PAID);
    }

    // ---------- 5. Notification best-effort ----------

    @Test
    void notificationInAppEnvoyeeAuxJoueursServis() {
        vipTicketService.generateVipTicketsForEvent(homeEventId);

        verify(notificationClient, times(2)).notifyUser(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.contains("billets VIP"),
                org.mockito.ArgumentMatchers.anyString());
    }

    // ---------- 6. Événement inconnu ----------

    @Test
    void evenementInconnuRejeteProprement() {
        assertThatThrownBy(() -> vipTicketService.generateVipTicketsForEvent(999999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non trouvé");
    }
}
