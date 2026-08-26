package com.wydad.digital.sports.controller;

import com.wydad.digital.sports.filter.SportsUserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 3 — limite de taille des médias tactiques (ISTQB valeur aux limites
 * + gestion d'erreur) :
 *  - ≤ 25 Mo accepté (limite incluse) ;
 *  - > 25 Mo rejeté 413 PAYLOAD_TOO_LARGE (handler MaxUploadSizeExceededException),
 *    jamais 500 ;
 *  - anonyme -> 401/403 (route réservée staff).
 *
 * H2 compatibilité PostgreSQL ; MockMvc avec le vrai filtre Spring Security.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:medialimit;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.servlet.multipart.max-file-size=25MB",
        "spring.servlet.multipart.max-request-size=26MB",
        "wydad.notification-service-uri=http://localhost:1"
})
@AutoConfigureMockMvc
class MediaUploadLimitTest {

    private static final String MEDIA_URL = "/api/sports/my-space/staff/media";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private com.wydad.digital.sports.repository.StaffRepository staffRepository;
    @Autowired
    private com.wydad.digital.sports.repository.PlayerRepository playerRepository;

    // Aucun appel HTTP réel : notifications best-effort simulées.
    @MockBean com.wydad.digital.sports.client.NotificationClient notificationClient;
    @MockBean com.wydad.digital.sports.client.AuthClient authClient;

    @BeforeEach
    void seedStaff() {
        // Le service exige une fiche Staff liée au compte (wholeTeam=true) :
        // sans elle -> 403 "Aucun profil staff lié" (défaut détecté par ce test).
        // DB_CLOSE_DELAY=-1 : la base survit entre tests -> upsert défensif.
        if (staffRepository.findByUserId(8L).isEmpty()) {
            staffRepository.save(com.wydad.digital.sports.model.Staff.builder()
                    .userId(8L).fullName("Coach Limite")
                    .role(com.wydad.digital.sports.enums.StaffRole.HEAD_COACH)
                    .sportType(com.wydad.digital.sports.enums.SportType.FOOTBALL)
                    .assignedCategory(com.wydad.digital.sports.enums.Category.SENIOR)
                    .build());
        }
        // wholeTeam=true notifie tous les joueurs de la catégorie du staff :
        // sans joueur U19 -> IllegalStateException "Aucun joueur dans votre catégorie".
        if (playerRepository.findBySportTypeAndCategory(
                com.wydad.digital.sports.enums.SportType.FOOTBALL,
                com.wydad.digital.sports.enums.Category.SENIOR).isEmpty()) {
            playerRepository.save(com.wydad.digital.sports.model.Player.builder()
                    .userId(300L).fullName("Joueur U19 Limite")
                    .sportType(com.wydad.digital.sports.enums.SportType.FOOTBALL)
                    .category(com.wydad.digital.sports.enums.Category.SENIOR)
                    .position("Milieu").jerseyNumber(10)
                    .build());
        }
    }

    @AfterEach
    void tearDown() {
        SportsUserContext.clear();
    }

    private byte[] bytesOf(int sizeMb) {
        return new byte[sizeMb * 1024 * 1024];
    }

    /** Headers d'identité exactement comme la gateway les transmet au service. */
    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
            asGatewayUser(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder b) {
        return b.header("X-User-Id", "8")
                .header("X-User-Email", "coach@wydad.ma")
                .header("X-User-Role", "ENTRAINEUR");
    }

    @Test
    @DisplayName("[Limite] Fichier de 24 Mo (< max) -> pas un 413")
    void fichierSousLaLimite() throws Exception {
        // wholeTeam=true : aucun joueur cible nécessaire ; le service peut
        // échouer plus loin (pas de fiche staff) mais JAMAIS par dépassement.
        mockMvc.perform(asGatewayUser(multipart(MEDIA_URL)
                        .file(new MockMultipartFile("file", "video.mp4", "video/mp4", bytesOf(24)))
                        .param("title", "Analyse match")
                        .param("wholeTeam", "true")))
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print())
                .andExpect(result -> {
                    int s = result.getResponse().getStatus();
                    org.assertj.core.api.Assertions.assertThat(s)
                            .as("sous la limite : tout sauf 403/413 (ici %d)", s)
                            .isNotIn(403, 413);
                });
    }

    @Test
    @DisplayName("[Limite] Fichier > 25 Mo -> 413, jamais 500")
    void fichierAuDelaDeLaLimite413() throws Exception {
        mockMvc.perform(asGatewayUser(multipart(MEDIA_URL)
                        .file(new MockMultipartFile("file", "grosse-video.mp4", "video/mp4",
                                bytesOf(26)))
                        .param("title", "Analyse trop lourde")
                        .param("wholeTeam", "true")))
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print())
                .andExpect(status().isPayloadTooLarge());
    }

    @Test
    @DisplayName("[Rôle] Rôle ENTRAINEUR mais AUCUNE fiche staff liée -> 403")
    void roleSansFicheStaffRefuse() throws Exception {
        // Partition : identité gateway correcte mais pas de profil Staff en base
        // (userId 777 sans fiche) — le service refuse, jamais de NPE/500.
        mockMvc.perform(multipart(MEDIA_URL)
                        .file(new MockMultipartFile("file", "v.mp4", "video/mp4", new byte[1024]))
                        .param("title", "Sans fiche")
                        .param("wholeTeam", "true")
                        .header("X-User-Id", "777")
                        .header("X-User-Email", "sansfiche@wydad.ma")
                        .header("X-User-Role", "ENTRAINEUR"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("[Rôle] Anonyme sur /staff/media -> refus d'accès")
    void anonymeRefuse() throws Exception {
        mockMvc.perform(multipart(MEDIA_URL)
                        .file(new MockMultipartFile("file", "v.mp4", "video/mp4", new byte[1024]))
                        .param("title", "Sans login")
                        .param("wholeTeam", "true"))
                .andExpect(status().is4xxClientError());
    }
}
