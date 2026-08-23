package com.wydad.digital.sports.controller;

import com.wydad.digital.sports.dto.PlayerDto;
import com.wydad.digital.sports.filter.SportsUserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Non-régression IDOR sur GET /api/sports/players/user/{userId} :
 * un joueur ne doit lire que sa propre fiche ; STAFF/ADMIN toutes.
 * Montage standalone avec le GlobalExceptionHandler réel du service
 * (AccessDeniedException -> 403).
 */
class PlayerControllerOwnershipTest {

    private MockMvc mockMvc;

    private final PlayerDto dto = new PlayerDto();

    @BeforeEach
    void setUp() {
        PlayerController controller = new PlayerController(
                Mockito.mock(com.wydad.digital.sports.service.PlayerService.class));
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        SportsUserContext.clear();
        SecurityContextHolder.clearContext();
    }

    private void loginAs(Long userId, String role) {
        // Simule le UserContextFilter de production : headers gateway -> contexte
        SportsUserContext.setCurrentUserId(userId);
        SportsUserContext.setCurrentUserRole(role);
        SportsUserContext.setCurrentUserEmail("user" + userId + "@test.ma");
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "user" + userId + "@test.ma", null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role)));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    @DisplayName("JOUEUR id 5 -> fiche du joueur id 9 : 403")
    void playerReadingAnotherPlayer_forbidden() throws Exception {
        loginAs(5L, "JOUEUR");
        mockMvc.perform(get("/api/sports/players/user/9"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("JOUEUR id 5 -> sa propre fiche : 200")
    void playerReadingOwnProfile_ok() throws Exception {
        loginAs(5L, "JOUEUR");
        mockMvc.perform(get("/api/sports/players/user/5"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("STAFF -> fiche de n'importe quel joueur : 200")
    void staffReadingAnyPlayer_ok() throws Exception {
        loginAs(50L, "STAFF");
        mockMvc.perform(get("/api/sports/players/user/9"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("ADMIN -> fiche de n'importe quel joueur : 200")
    void adminReadingAnyPlayer_ok() throws Exception {
        loginAs(1L, "ADMIN");
        mockMvc.perform(get("/api/sports/players/user/9"))
                .andExpect(status().isOk());
    }
}
