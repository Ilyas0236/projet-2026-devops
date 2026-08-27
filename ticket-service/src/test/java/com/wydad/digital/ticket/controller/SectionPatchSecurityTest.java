package com.wydad.digital.ticket.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests de sécurité + métier du PATCH /api/ticket/sections/{id}.
 *
 * <p>Contexte : la grille tarifaire des sections est gérée par l'ADMIN. Le
 * PATCH permet de corriger le prix d'une section existante SANS casser les
 * billets déjà vendus (PUT sur l'event supprime+recrée les sections, ce qui
 * viole la FK tickets.section_id dès qu'un billet existe).</p>
 *
 * <p>Table de décision (rôle × PATCH section) :</p>
 * <pre>
 *   Rôle     | PATCH /sections/{id} attendu
 *   ---------|--------------------------------
 *   Anonyme  | 403
 *   ADHERENT | 403
 *   STAFF    | 403 (pas de pouvoir d'édition)
 *   ADMIN    | 200
 * </pre>
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:sectionpatch;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@AutoConfigureMockMvc
@Transactional
class SectionPatchSecurityTest {

    private static final String BASE = "/api/ticket/sections";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private SectionRepository sectionRepository;
    @Autowired private EventRepository eventRepository;

    private Long sectionId;

    @BeforeEach
    void seed() {
        Event event = eventRepository.save(Event.builder()
                .title("WAC - Test PATCH")
                .eventType(com.wydad.digital.ticket.enums.EventType.FOOTBALL)
                .homeTeam("Wydad AC")
                .awayTeam("Raja CA")
                .venue("Stade Mohammed V")
                .eventDate(LocalDateTime.now().plusDays(10))
                .basePrice(new BigDecimal("50.00"))
                .totalCapacity(1000)
                .availableSeats(1000)
                .soldTickets(0)
                .build());

        Section s = sectionRepository.save(Section.builder()
                .name("Tribune Test")
                .category(TicketCategory.TRIBUNE_OFFICIELLE)
                .capacity(500)
                .availableSeats(500)
                .price(new BigDecimal("0.00")) // le bug observé en prod
                .event(event)
                .build());
        sectionId = s.getId();
    }

    @Test
    @DisplayName("[TD] PATCH section en anonyme -> 403")
    void patchAnonymeRefuse() throws Exception {
        mockMvc.perform(patch(BASE + "/" + sectionId)
                        .contentType("application/json")
                        .content("{\"price\": 100}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("[TD] PATCH section par ADHERENT -> 403")
    void patchAdherentRefuse() throws Exception {
        mockMvc.perform(patch(BASE + "/" + sectionId)
                        .header("X-User-Id", "42")
                        .header("X-User-Email", "fan@wydad.ma")
                        .header("X-User-Role", "ADHERENT")
                        .contentType("application/json")
                        .content("{\"price\": 100}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("[TD] PATCH section par STAFF -> 403 (STAFF peut scanner, pas éditer la grille)")
    void patchStaffRefuse() throws Exception {
        mockMvc.perform(patch(BASE + "/" + sectionId)
                        .header("X-User-Id", "7")
                        .header("X-User-Email", "staff@wydad.ma")
                        .header("X-User-Role", "STAFF")
                        .contentType("application/json")
                        .content("{\"price\": 100}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ADMIN PATCH le prix d'une section -> 200 + prix persisté")
    void adminPatchPrixOk() throws Exception {
        Map<String, Object> body = Map.of("price", new BigDecimal("300.00"));

        mockMvc.perform(patch(BASE + "/" + sectionId)
                        .header("X-User-Id", "1")
                        .header("X-User-Email", "admin@wydad.ma")
                        .header("X-User-Role", "ADMIN")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(sectionId))
                .andExpect(jsonPath("$.price").value(300.00));

        Section persisted = sectionRepository.findById(sectionId).orElseThrow();
        assertThat(persisted.getPrice()).isEqualByComparingTo("300.00");
    }

    @Test
    @DisplayName("ADMIN PATCH un prix ≤ 0 -> 400 (IllegalArgumentException)")
    void adminPatchPrixInvalide() throws Exception {
        Map<String, Object> body = Map.of("price", new BigDecimal("0.00"));

        mockMvc.perform(patch(BASE + "/" + sectionId)
                        .header("X-User-Id", "1")
                        .header("X-User-Email", "admin@wydad.ma")
                        .header("X-User-Role", "ADMIN")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("ADMIN PATCH une section inexistante -> 404")
    void adminPatchSectionInexistante() throws Exception {
        Map<String, Object> body = Map.of("price", new BigDecimal("100.00"));

        mockMvc.perform(patch(BASE + "/999999")
                        .header("X-User-Id", "1")
                        .header("X-User-Email", "admin@wydad.ma")
                        .header("X-User-Role", "ADMIN")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNotFound());
    }
}
