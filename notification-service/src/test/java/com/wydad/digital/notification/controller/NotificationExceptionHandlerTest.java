package com.wydad.digital.notification.controller;

import com.wydad.digital.notification.filter.UserContext;
import com.wydad.digital.notification.service.NotificationOrchestrator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 0-BIS.3 : erreurs métier du notification-service -> statut + corps JSON
 * {error, message, timestamp} cohérents (404 au lieu d'une trace brute).
 * Montage standalone avec le GlobalExceptionHandler réel.
 */
class NotificationExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        NotificationOrchestrator orch = Mockito.mock(NotificationOrchestrator.class);
        when(orch.getById(42L)).thenThrow(
                new jakarta.persistence.EntityNotFoundException("Notification non trouvée"));
        mockMvc = MockMvcBuilders.standaloneSetup(new NotificationController(orch, Mockito.mock(com.wydad.digital.notification.config.InternalSecretValidator.class)))
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
    @DisplayName("Notification inexistante -> 404 NOT_FOUND + corps JSON homogène")
    void unknownNotification_returns404WithJsonBody() throws Exception {
        mockMvc.perform(patch("/api/notification/42/read"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Notification non trouvée"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("Lecture des notifications d'un autre utilisateur -> 403 FORBIDDEN")
    void readingAnotherUsersNotifications_forbidden() throws Exception {
        mockMvc.perform(get("/api/notification/user/9"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }
}
