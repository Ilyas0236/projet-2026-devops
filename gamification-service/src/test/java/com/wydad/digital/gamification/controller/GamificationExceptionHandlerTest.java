package com.wydad.digital.gamification.controller;

import com.wydad.digital.gamification.filter.UserContext;
import com.wydad.digital.gamification.service.GamificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 0-BIS.3 : erreurs métier du gamification-service -> statut + corps JSON
 * {error, message, timestamp} cohérents (400 au lieu d'une trace brute).
 * Montage standalone avec le GlobalExceptionHandler réel.
 */
class GamificationExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        GamificationService svc = Mockito.mock(GamificationService.class);
        Mockito.when(svc.submitPrediction(any()))
                .thenThrow(new IllegalArgumentException("Pronostic déjà soumis pour ce match"));
        mockMvc = MockMvcBuilders.standaloneSetup(new GamificationController(svc))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        // Simule le UserContextFilter de production
        UserContext.setCurrentUserId(7L);
        UserContext.setCurrentUserRole("ADHERENT");
        UserContext.setCurrentUserEmail("fan@test.ma");
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("Pronostic déjà soumis -> 400 BAD_REQUEST + corps JSON homogène")
    void duplicatePrediction_returns400WithJsonBody() throws Exception {
        mockMvc.perform(post("/api/gamification/predictions")
                        .contentType("application/json")
                        .content("{\"matchId\":3,\"predictedHomeScore\":2,\"predictedAwayScore\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Pronostic déjà soumis pour ce match"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("Consultation des points d'un autre utilisateur -> 403 FORBIDDEN")
    void readingAnotherUsersPoints_forbidden() throws Exception {
        mockMvc.perform(get("/api/gamification/points/9"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }
}
