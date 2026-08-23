package com.wydad.digital.gamification.controller;

import com.wydad.digital.gamification.client.ContentClient;
import com.wydad.digital.gamification.client.NotificationClient;
import com.wydad.digital.gamification.model.BadgeDefinition;
import com.wydad.digital.gamification.repository.BadgeDefinitionRepository;
import com.wydad.digital.gamification.repository.UserBadgeRepository;
import com.wydad.digital.gamification.repository.UserPointsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * B.8 — Preuves serveur des règles badges :
 * - catalogue actif lisible par tout utilisateur authentifié (200) ;
 * - écriture sans identité -> 403 (chaîne Spring Security, aucun
 *   AuthenticationEntryPoint personnalise) ;
 * - écriture avec role non-ADMIN (ADHERENT) -> 403 ;
 * - écriture ADMIN -> 201 et persistance reelle ;
 * - validation : code / nom / seuil obligatoires -> 400 ;
 * - attribution AUTOMATIQUE : franchir le seuil de points attribue le badge
 *   (via addPoints), et il est impossible d'obtenir un badge sans franchir
 *   son seuil (aucune route d'attribution manuelle n'existe).
 *
 * Identite injectee via X-User-Id + X-User-Email + X-User-Role (chaine reelle
 * du UserContextFilter). ContentClient et NotificationClient mockes.
 */
@SpringBootTest(properties = {
        // H2 en memoire pour les tests ; PostgreSQL revalide au deploiement
        "spring.datasource.url=jdbc:h2:mem:gamification_badges;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@AutoConfigureMockMvc
@Transactional
class BadgeSecurityTest {

    private static final String BASE = "/api/gamification/badges";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BadgeDefinitionRepository badgeRepository;

    @Autowired
    private UserBadgeRepository userBadgeRepository;

    @Autowired
    private UserPointsRepository userPointsRepository;

    @MockBean
    private ContentClient contentClient;

    @MockBean
    private NotificationClient notificationClient;

    @BeforeEach
    void cleanBase() {
        userBadgeRepository.deleteAll();
        badgeRepository.deleteAll();
        userPointsRepository.deleteAll();
        Mockito.doNothing().when(notificationClient).notifyUser(
                anyLong(), any(), anyString(), anyString(), any());
    }

    private static String badgeBody(String code, String name, Integer minPoints) {
        return """
                {"code": "%s", "name": "%s", "description": "Badge de test", \
                "minPoints": %s}"""
                .formatted(code, name, minPoints == null ? "null" : minPoints);
    }

    @Test
    void catalogueLisibleParUtilisateurAuthentifie() throws Exception {
        badgeRepository.save(BadgeDefinition.builder()
                .code("FIDELE").name("Fidèle").minPoints(100).active(true).build());
        badgeRepository.save(BadgeDefinition.builder()
                .code("CACHE").name("Badge désactivé").minPoints(500).active(false).build());

        mockMvc.perform(get(BASE)
                        .header("X-User-Id", "42")
                        .header("X-User-Email", "fan@wydad.ma")
                        .header("X-User-Role", "ADHERENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].code").value("FIDELE"));
    }

    @Test
    void creationSansIdentiteRefuseeEtRienEnBase() throws Exception {
        mockMvc.perform(post(BASE)
                        .contentType("application/json")
                        .content(badgeBody("FANTOME", "Badge fantôme", 100)))
                .andExpect(status().isForbidden());
        assertThat(badgeRepository.count()).isZero();
    }

    @Test
    void creationAvecRoleAdherentRefusee403() throws Exception {
        mockMvc.perform(post(BASE)
                        .header("X-User-Id", "42")
                        .header("X-User-Email", "fan@wydad.ma")
                        .header("X-User-Role", "ADHERENT")
                        .contentType("application/json")
                        .content(badgeBody("PIRATE", "Badge pirate", 100)))
                .andExpect(status().isForbidden());
        assertThat(badgeRepository.count()).isZero();
    }

    @Test
    void adminPeutCreerUnBadgePersiste() throws Exception {
        mockMvc.perform(post(BASE)
                        .header("X-User-Id", "999")
                        .header("X-User-Email", "admin@wydad.ma")
                        .header("X-User-Role", "ADMIN")
                        .contentType("application/json")
                        .content(badgeBody("PREMIER_PALIER", "Premier palier", 50)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("PREMIER_PALIER"))
                .andExpect(jsonPath("$.active").value(true));

        assertThat(badgeRepository.count()).isEqualTo(1);
    }

    @Test
    void creationSansCodeNomOuSeuilRenvoie400() throws Exception {
        // code manquant
        mockMvc.perform(post(BASE)
                        .header("X-User-Id", "999")
                        .header("X-User-Email", "admin@wydad.ma")
                        .header("X-User-Role", "ADMIN")
                        .contentType("application/json")
                        .content(badgeBody("", "Badge X", 100)))
                .andExpect(status().isBadRequest());

        // nom manquant
        mockMvc.perform(post(BASE)
                        .header("X-User-Id", "999")
                        .header("X-User-Email", "admin@wydad.ma")
                        .header("X-User-Role", "ADMIN")
                        .contentType("application/json")
                        .content(badgeBody("BADGE_X", "", 100)))
                .andExpect(status().isBadRequest());

        // seuil manquant
        mockMvc.perform(post(BASE)
                        .header("X-User-Id", "999")
                        .header("X-User-Email", "admin@wydad.ma")
                        .header("X-User-Role", "ADMIN")
                        .contentType("application/json")
                        .content(badgeBody("BADGE_X", "Badge X", null)))
                .andExpect(status().isBadRequest());

        assertThat(badgeRepository.count()).isZero();
    }

    @Test
    void franchirLeSeuilDePointsAttribueAutomatiquementLeBadge() throws Exception {
        badgeRepository.save(BadgeDefinition.builder()
                .code("FIDELE").name("Fidèle").minPoints(50).active(true).build());

        // L'utilisateur gagne 60 points (bonus ADMIN) -> le badge est attribué
        mockMvc.perform(post("/api/gamification/points/add?userId=42&amount=60")
                        .header("X-User-Id", "999")
                        .header("X-User-Email", "admin@wydad.ma")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk());

        assertThat(userBadgeRepository.count()).isEqualTo(1);
        assertThat(userPointsRepository.findById(42L).orElseThrow().getTotalPoints()).isEqualTo(60);

        // L'utilisateur voit son badge
        mockMvc.perform(get(BASE + "/user/42")
                        .header("X-User-Id", "42")
                        .header("X-User-Email", "fan@wydad.ma")
                        .header("X-User-Role", "ADHERENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].badge.code").value("FIDELE"));
    }

    @Test
    void aucunBadgeSansFranchissementDeSeuilEtPasDeDoubleAttribution() throws Exception {
        badgeRepository.save(BadgeDefinition.builder()
                .code("VETERAN").name("Vétéran").minPoints(500).active(true).build());

        // 60 points : bien en dessous du seuil -> aucun badge attribué
        mockMvc.perform(post("/api/gamification/points/add?userId=42&amount=60")
                        .header("X-User-Id", "999")
                        .header("X-User-Email", "admin@wydad.ma")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk());
        assertThat(userBadgeRepository.count()).isZero();

        // Un second gain franchit le seuil -> attribution unique (pas de doublon)
        mockMvc.perform(post("/api/gamification/points/add?userId=42&amount=500")
                        .header("X-User-Id", "999")
                        .header("X-User-Email", "admin@wydad.ma")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk());

        assertThat(userBadgeRepository.count()).isEqualTo(1);
    }

    @Test
    void utilisateurNePeutPasConsulterLesBadgesDunAutre() throws Exception {
        mockMvc.perform(get(BASE + "/user/77")
                        .header("X-User-Id", "42")
                        .header("X-User-Email", "fan@wydad.ma")
                        .header("X-User-Role", "ADHERENT"))
                .andExpect(status().isForbidden());
    }
}
