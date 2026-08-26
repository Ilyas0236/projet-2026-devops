package com.wydad.digital.ticket.service;

import com.wydad.digital.ticket.client.AuthClient;
import com.wydad.digital.ticket.client.NotificationClient;
import com.wydad.digital.ticket.client.SportsRosterClient;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Phase 2 (E) / §3-§4 — Tests métier de la génération automatique des
 * billets VIP :
 *
 * 1. 4 billets VIP par joueur SENIOR du groupe, à domicile uniquement ;
 * 2. match à l'extérieur → aucun billet, rejet explicite ;
 * 3. régénération idempotente → aucun doublon (cas ISTQB « billet dupliqué ») ;
 * 4. prix 0 + statut PAID : hors circuit E-cash, jamais débité ;
 * 5. notification in-app best-effort envoyée à chaque joueur servi ;
 * 6. §24/§26 : seuls les joueurs du groupe discipline+catégorie sont servis,
 *    catégories jeunes → 2 billets.
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
    private SportsRosterClient rosterClient;

    @MockBean
    private NotificationClient notificationClient;

    private Long homeEventId;
    private Long awayEventId;
    private Long vipSectionId;

    @BeforeAll
    void seedEvents() {
        // Match à DOMICILE avec section VIP — SANS catégorie (historique :
        // repli sur tous les joueurs actifs, quota SENIOR)
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

    // ---------- 1bis. §24/§26 — filtrage par groupe + quotas ----------

    /**
     * Un événement AVEC catégorie ne sert que les joueurs du roster du
     * groupe discipline+catégorie — jamais un joueur d'un autre groupe.
     */
    @Test
    void evenementAvecCategorieNeSertQueLesJoueursDuGroupe() {
        Event u17 = eventRepository.save(Event.builder()
                .title("WAC U17 - Difaa El Jadidi (groupe test)")
                .eventType(com.wydad.digital.ticket.enums.EventType.FOOTBALL)
                .category(com.wydad.digital.ticket.model.EventCategory.U17)
                .homeTeam("Wydad AC")
                .awayTeam("Difaa El Jadidi")
                .venue("Complexe Mohammed V")
                .eventDate(LocalDateTime.now().plusDays(8))
                .basePrice(new BigDecimal("40.00"))
                .totalCapacity(60)
                .availableSeats(60)
                .soldTickets(0)
                .build());
        sectionRepository.save(Section.builder()
                .name("VIP U17 (test)")
                .category(TicketCategory.VIP)
                .capacity(30)
                .availableSeats(30)
                .price(BigDecimal.ZERO)
                .event(u17)
                .build());

        // Le roster serveur ne renvoie que les DEUX joueurs Football U17.
        org.mockito.BDDMockito.given(rosterClient.fetchPlayersOfGroup("FOOTBALL", "U17"))
                .willReturn(List.of(
                        new SportsRosterClient.RosterMember(JOUEUR_1_ID, "Youssef El Amrani", "JOUEUR"),
                        new SportsRosterClient.RosterMember(JOUEUR_2_ID, "Omar Naji", "JOUEUR")));

        var result = vipTicketService.generateVipTicketsForEvent(u17.getId());

        // Catégorie jeune : 2 billets par joueur, PAS 4 (§4).
        assertThat(result.joueursServis()).isEqualTo(2);
        assertThat(result.billetsCrees()).isEqualTo(4);

        // Le client auth n'a PAS été consulté : la source de vérité est le roster.
        verify(authClient, never()).fetchActivePlayers();

        var billetsJ1 = ticketRepository.findByUserIdOrderByCreatedAtDesc(JOUEUR_1_ID).stream()
                .filter(t -> t.getEvent().getId().equals(u17.getId())).toList();
        assertThat(billetsJ1).hasSize(2);
    }

    /** Quota SENIOR = 4 billets (§3), catégorie jeune = 2 billets (§4). */
    @Test
    void quotaSeniorQuatreBilletsEtJeuneDeuxBillets() {
        assertThat(VipTicketService.billetsPourCategorie("SENIOR")).isEqualTo(4);
        assertThat(VipTicketService.billetsPourCategorie("U15")).isEqualTo(2);
        assertThat(VipTicketService.billetsPourCategorie("U17")).isEqualTo(2);
        assertThat(VipTicketService.billetsPourCategorie("U20")).isEqualTo(2);
    }

    /** Roster vide (groupe sans joueur) : génération propre, zéro billet. */
    @Test
    void groupeSansJoueurNeGenereRien() {
        org.mockito.BDDMockito.given(rosterClient.fetchPlayersOfGroup(anyString(), anyString()))
                .willReturn(List.of());

        Event senior = eventRepository.save(Event.builder()
                .title("WAC - OC Khouribga (senior test)")
                .eventType(com.wydad.digital.ticket.enums.EventType.FOOTBALL)
                .category(com.wydad.digital.ticket.model.EventCategory.SENIOR)
                .homeTeam("Wydad AC")
                .awayTeam("OC Khouribga")
                .venue("Complexe Mohammed V")
                .eventDate(LocalDateTime.now().plusDays(6))
                .basePrice(new BigDecimal("100.00"))
                .totalCapacity(100)
                .availableSeats(100)
                .soldTickets(0)
                .build());
        sectionRepository.save(Section.builder()
                .name("VIP senior (test)")
                .category(TicketCategory.VIP)
                .capacity(30)
                .availableSeats(30)
                .price(BigDecimal.ZERO)
                .event(senior)
                .build());

        var result = vipTicketService.generateVipTicketsForEvent(senior.getId());

        assertThat(result.joueursServis()).isZero();
        assertThat(result.billetsCrees()).isZero();
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
                org.mockito.ArgumentMatchers.contains("VIP"),
                org.mockito.ArgumentMatchers.anyString());
    }

    // ---------- 6. Événement inconnu ----------

    @Test
    void evenementInconnuRejeteProprement() {
        assertThatThrownBy(() -> vipTicketService.generateVipTicketsForEvent(999999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non trouvé");
    }

    // ---------- 7. Auto-déclenchement à la création ----------

    /**
     * L'auto-génération sur un match à domicile produit bien les billets
     * (même résultat qu'un appel manuel).
     */
    @Test
    void autoGenerationSurMatchADomicileCreeLesBillets() {
        Event home2 = eventRepository.save(Event.builder()
                .title("WAC - Berkane (auto test)")
                .eventType(com.wydad.digital.ticket.enums.EventType.FOOTBALL)
                .homeTeam("Wydad AC")
                .awayTeam("RS Berkane")
                .venue("Complexe Mohammed V")
                .eventDate(LocalDateTime.now().plusDays(15))
                .basePrice(new BigDecimal("100.00"))
                .totalCapacity(100)
                .availableSeats(100)
                .soldTickets(0)
                .build());
        sectionRepository.save(Section.builder()
                .name("VIP auto test")
                .category(TicketCategory.VIP)
                .capacity(40)
                .availableSeats(40)
                .price(BigDecimal.ZERO)
                .event(home2)
                .build());

        vipTicketService.autoGenerateIfHomeEvent(home2);

        var billets = ticketRepository.findByUserIdOrderByCreatedAtDesc(JOUEUR_1_ID).stream()
                .filter(t -> t.getEvent().getId().equals(home2.getId()))
                .toList();
        assertThat(billets).hasSize(4);
    }

    /** Un match à l'extérieur ne déclenche RIEN, sans erreur. */
    @Test
    void autoGenerationSurMatchExterieurNeRienFait() {
        Event away = eventRepository.findById(awayEventId).orElseThrow();
        int avant = ticketRepository.findAll().size();

        vipTicketService.autoGenerateIfHomeEvent(away);

        assertThat(ticketRepository.findAll()).hasSize(avant);
    }

    /** Section VIP absente : l'auto-génération avale l'erreur (best-effort). */
    @Test
    void autoGenerationSansSectionVipNEchouePas() {
        Event home3 = eventRepository.save(Event.builder()
                .title("WAC - Hassania (sans VIP)")
                .eventType(com.wydad.digital.ticket.enums.EventType.FOOTBALL)
                .homeTeam("Wydad AC")
                .awayTeam("Hassania Agadir")
                .venue("Complexe Mohammed V")
                .eventDate(LocalDateTime.now().plusDays(20))
                .basePrice(new BigDecimal("90.00"))
                .totalCapacity(80)
                .availableSeats(80)
                .soldTickets(0)
                .build());

        org.assertj.core.api.Assertions.assertThatCode(
                () -> vipTicketService.autoGenerateIfHomeEvent(home3))
                .doesNotThrowAnyException();
        // Aucun billet créé (pas de section)
        assertThat(ticketRepository.findByUserIdOrderByCreatedAtDesc(JOUEUR_1_ID)).isEmpty();
    }
}
