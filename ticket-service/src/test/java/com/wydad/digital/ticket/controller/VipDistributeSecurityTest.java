package com.wydad.digital.ticket.controller;

import com.wydad.digital.ticket.client.SportsRosterClient;
import com.wydad.digital.ticket.enums.TicketCategory;
import com.wydad.digital.ticket.model.Event;
import com.wydad.digital.ticket.model.Section;
import com.wydad.digital.ticket.repository.EventRepository;
import com.wydad.digital.ticket.repository.SectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * B.29 — Tests de sécurité de l'endpoint ADMIN
 * {@code POST /api/ticket/tickets/events/{eventId}/vip-distribute}.
 *
 * <p>Table de décision (rôle × POST /vip-distribute) :</p>
 * <pre>
 *   Rôle        | POST /vip-distribute attendu
 *   ------------|--------------------------------
 *   Anonyme     | 401
 *   JOUEUR      | 403
 *   STAFF       | 403 (peut scanner, pas distribuer)
 *   ENTRAINEUR  | 403 (reçoit ses billets mais ne peut pas les distribuer)
 *   ADHERENT    | 403
 *   ADMIN       | 200 (compteurs bénéficiairesServis + billetsCrees)
 * </pre>
 *
 * <p>L'endpoint n'est PAS protégé par gateway (pas dans {@code /internal/**}),
 * il passe par le filtre JWT standard — comme les autres routes billetterie
 * admin. La sécurité est uniquement portée par {@code @PreAuthorize}.</p>
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:vipdistribute;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@AutoConfigureMockMvc
class VipDistributeSecurityTest {

    private static final String BASE = "/api/ticket/tickets/events";

    @Autowired private MockMvc mockMvc;
    @Autowired private EventRepository eventRepository;
    @Autowired private SectionRepository sectionRepository;

    @MockBean
    private SportsRosterClient rosterClient;

    private Long homeEventId;

    @BeforeEach
    void seed() {
        Event home = eventRepository.save(Event.builder()
                .title("WAC - Test B.29 vip-distribute")
                .eventType(com.wydad.digital.ticket.enums.EventType.FOOTBALL)
                .category(com.wydad.digital.ticket.model.EventCategory.SENIOR)
                .homeTeam("Wydad AC")
                .awayTeam("Raja CA")
                .venue("Complexe Mohammed V")
                .eventDate(LocalDateTime.now().plusDays(10))
                .basePrice(new BigDecimal("150.00"))
                .totalCapacity(200)
                .availableSeats(200)
                .soldTickets(0)
                .build());
        sectionRepository.save(Section.builder()
                .name("VIP (B.29 test)")
                .category(TicketCategory.VIP)
                .capacity(50)
                .availableSeats(50)
                .price(BigDecimal.ZERO)
                .event(home)
                .build());
        this.homeEventId = home.getId();

        // Stub par défaut : 1 joueur SENIOR + 1 staff SENIOR = 2 bénéficiaires, 8 billets.
        org.mockito.BDDMockito.given(rosterClient.fetchMembersOfGroup("FOOTBALL", "SENIOR"))
                .willReturn(List.of(
                        new SportsRosterClient.RosterMember(101L, "Youssef El Amrani", "JOUEUR"),
                        new SportsRosterClient.RosterMember(202L, "Khalid Lahlou", "STAFF")));
    }

    @Test
    @DisplayName("[TD] Anonyme -> 403 (Spring @PreAuthorize refuse par défaut)")
    void vipDistributeAnonymeRefuse() throws Exception {
        mockMvc.perform(post(BASE + "/" + homeEventId + "/vip-distribute"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("[TD] JOUEUR -> 403 (reçoit ses billets mais ne peut pas en distribuer)")
    void vipDistributeJoueurRefuse() throws Exception {
        mockMvc.perform(post(BASE + "/" + homeEventId + "/vip-distribute")
                        .header("X-User-Id", "101")
                        .header("X-User-Email", "hakim@wydad.ma")
                        .header("X-User-Role", "JOUEUR"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("[TD] STAFF -> 403 (peut scanner, pas distribuer)")
    void vipDistributeStaffRefuse() throws Exception {
        mockMvc.perform(post(BASE + "/" + homeEventId + "/vip-distribute")
                        .header("X-User-Id", "7")
                        .header("X-User-Email", "staff@wydad.ma")
                        .header("X-User-Role", "STAFF"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("[TD] ENTRAINEUR -> 403 (reçoit ses billets mais ne peut pas en distribuer)")
    void vipDistributeEntraineurRefuse() throws Exception {
        mockMvc.perform(post(BASE + "/" + homeEventId + "/vip-distribute")
                        .header("X-User-Id", "8")
                        .header("X-User-Email", "lopez@wydad.ma")
                        .header("X-User-Role", "ENTRAINEUR"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("[TD] ADHERENT -> 403 (supporter, ne distribue rien)")
    void vipDistributeAdherentRefuse() throws Exception {
        mockMvc.perform(post(BASE + "/" + homeEventId + "/vip-distribute")
                        .header("X-User-Id", "42")
                        .header("X-User-Email", "fan@wydad.ma")
                        .header("X-User-Role", "ADHERENT"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ADMIN -> 200 + beneficiairesServis/billetsCrees (JOUEUR + STAFF)")
    void vipDistributeAdminOk() throws Exception {
        mockMvc.perform(post(BASE + "/" + homeEventId + "/vip-distribute")
                        .header("X-User-Id", "1")
                        .header("X-User-Email", "admin@wydad.ma")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.beneficiairesServis").value(2))
                .andExpect(jsonPath("$.billetsCrees").value(8));
    }

    @Test
    @DisplayName("ADMIN sur event inexistant -> 404")
    void vipDistributeEventInexistant() throws Exception {
        mockMvc.perform(post(BASE + "/999999/vip-distribute")
                        .header("X-User-Id", "1")
                        .header("X-User-Email", "admin@wydad.ma")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isNotFound());
    }
}
