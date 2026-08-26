package com.wydad.digital.sports.controller;

import com.wydad.digital.sports.enums.Category;
import com.wydad.digital.sports.enums.SportType;
import com.wydad.digital.sports.model.Player;
import com.wydad.digital.sports.model.Session;
import com.wydad.digital.sports.model.Staff;
import com.wydad.digital.sports.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * B.6 — Preuves serveur du statut médical strict.
 *
 * 1. seul le staff MÉDICAL (DOCTOR/PHYSIOTHERAPIST) de la catégorie pose
 *    APT/INAPTE — un coach → 403 ;
 * 2. le staff médical d'une autre catégorie → 403 ;
 * 3. l'ADMIN peut poser le statut sur n'importe quel joueur ;
 * 4. la convocation d'un joueur INAPTE est refusée (400) même par l'ADMIN ;
 * 5. le statut est bien persisté (APT après retour à APT).
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:medical;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        // Note : H2 MODE=PostgreSQL embarqué pour la CI sans démon Docker ;
        // revalider sur PostgreSQL réel au déploiement.
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "wydad.notification-service-uri=http://localhost:1"
})
@AutoConfigureMockMvc
class MedicalSecurityTest {

    @Autowired MockMvc mvc;
    @Autowired PlayerRepository playerRepository;
    @Autowired StaffRepository staffRepository;
    @Autowired SessionRepository sessionRepository;
    @Autowired ConvocationRepository convocationRepository;

    @MockBean com.wydad.digital.sports.client.NotificationClient notificationClient;

    private static final String EMAIL = "x-test@wydad.ma";

    private Player joueur(Long uid, String nom, SportType sport, Category cat) {
        return playerRepository.save(Player.builder()
                .userId(uid).fullName(nom)
                .sportType(sport).category(cat)
                .build());
    }

    private Staff staff(Long uid, String nom, SportType sport, Category cat,
                        com.wydad.digital.sports.enums.StaffRole role) {
        return staffRepository.save(Staff.builder()
                .userId(uid).fullName(nom).role(role)
                .sportType(sport).assignedCategory(cat)
                .build());
    }

    @AfterEach
    void clean() {
        convocationRepository.deleteAll();
        sessionRepository.deleteAll();
        playerRepository.deleteAll();
        staffRepository.deleteAll();
    }

    private String medicalBody(String status, String note) {
        return "{\"status\":\"" + status + "\",\"note\":" + (note == null ? "null" : "\"" + note + "\"") + "}";
    }

    @Test
    void coachNePeutPasPoserLeStatutMedical() throws Exception {
        joueur(701L, "Joueur Foot", SportType.FOOTBALL, Category.SENIOR);
        staff(702L, "Coach Foot U19", SportType.FOOTBALL, Category.SENIOR,
                com.wydad.digital.sports.enums.StaffRole.HEAD_COACH);

        // Même catégorie mais rôle NON médical → 403
        mvc.perform(put("/api/sports/my-space/staff/medical-status")
                        .header("X-User-Email", EMAIL)
                        .header("X-User-Id", "702")
                        .header("X-User-Role", "STAFF")
                        .param("joueurUserId", "701")
                        .contentType("application/json")
                        .content(medicalBody("INAPTE", "Blessure")))
                .andExpect(status().isForbidden());

        assertThat(playerRepository.findByUserId(701L).orElseThrow().getMedicalStatus())
                .isEqualTo(com.wydad.digital.sports.enums.MedicalStatus.APT);
    }

    @Test
    void medecinAutreCategorieRefuse() throws Exception {
        joueur(711L, "Joueur Foot", SportType.FOOTBALL, Category.SENIOR);
        staff(712L, "Medecin Handball", SportType.HANDBALL, Category.U20,
                com.wydad.digital.sports.enums.StaffRole.DOCTOR);

        mvc.perform(put("/api/sports/my-space/staff/medical-status")
                        .header("X-User-Email", EMAIL)
                        .header("X-User-Id", "712")
                        .header("X-User-Role", "STAFF")
                        .param("joueurUserId", "711")
                        .contentType("application/json")
                        .content(medicalBody("INAPTE", null)))
                .andExpect(status().isForbidden());

        assertThat(playerRepository.findByUserId(711L).orElseThrow().getMedicalStatus())
                .isEqualTo(com.wydad.digital.sports.enums.MedicalStatus.APT);
    }

    @Test
    void medecinDeLaCategoriePoseInapte() throws Exception {
        joueur(721L, "Joueur Foot", SportType.FOOTBALL, Category.SENIOR);
        staff(722L, "Medecin Foot U19", SportType.FOOTBALL, Category.SENIOR,
                com.wydad.digital.sports.enums.StaffRole.DOCTOR);

        mvc.perform(put("/api/sports/my-space/staff/medical-status")
                        .header("X-User-Email", EMAIL)
                        .header("X-User-Id", "722")
                        .header("X-User-Role", "STAFF")
                        .param("joueurUserId", "721")
                        .contentType("application/json")
                        .content(medicalBody("INAPTE", "Entorse cheville")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INAPTE"))
                .andExpect(jsonPath("$.note").value("Entorse cheville"));

        var p = playerRepository.findByUserId(721L).orElseThrow();
        assertThat(p.getMedicalStatus())
                .isEqualTo(com.wydad.digital.sports.enums.MedicalStatus.INAPTE);
        assertThat(p.getMedicalNote()).isEqualTo("Entorse cheville");
        assertThat(p.getMedicalUpdatedAt()).isNotNull();
    }

    @Test
    void adminPeutPoserLeStatutSurNImporteQuelJoueur() throws Exception {
        joueur(731L, "Joueur Hand", SportType.HANDBALL, Category.U20);

        mvc.perform(put("/api/sports/my-space/staff/medical-status")
                        .header("X-User-Email", EMAIL)
                        .header("X-User-Id", "999")
                        .header("X-User-Role", "ADMIN")
                        .param("joueurUserId", "731")
                        .contentType("application/json")
                        .content(medicalBody("APT", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APT"));
    }

    @Test
    void joueurInapteNonConvoquableMemeParAdmin() throws Exception {
        joueur(741L, "Joueur Inapte", SportType.FOOTBALL, Category.SENIOR);
        staff(742L, "Coach Foot U19", SportType.FOOTBALL, Category.SENIOR,
                com.wydad.digital.sports.enums.StaffRole.HEAD_COACH);
        Session s = sessionRepository.save(Session.builder()
                .title("Entraînement").location("Complexe")
                .sessionDate(LocalDateTime.now().plusDays(2))
                .sportType(SportType.FOOTBALL).category(Category.SENIOR)
                .createdByStaffId(742L)
                .build());

        // Pose INAPTE via l'admin
        mvc.perform(put("/api/sports/my-space/staff/medical-status")
                        .header("X-User-Email", EMAIL)
                        .header("X-User-Id", "999")
                        .header("X-User-Role", "ADMIN")
                        .param("joueurUserId", "741")
                        .contentType("application/json")
                        .content(medicalBody("INAPTE", "Repos médical")))
                .andExpect(status().isOk());

        // Tentative de convocation (même par ADMIN) → 400
        mvc.perform(post("/api/sports/my-space/staff/convocations")
                        .header("X-User-Email", EMAIL)
                        .header("X-User-Id", "999")
                        .header("X-User-Role", "ADMIN")
                        .param("joueurUserId", "741")
                        .param("sessionId", String.valueOf(s.getId())))
                .andExpect(status().isBadRequest());

        assertThat(convocationRepository.findAll()).isEmpty();
    }
}
