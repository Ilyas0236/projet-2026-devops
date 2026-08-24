package com.wydad.digital.notification.controller;

import com.wydad.digital.notification.repository.NewsletterSubscriberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Newsletter publique — preuves serveur de l'inscription anonyme.
 * Conception selon ISTQB Foundation Level :
 *
 *  - Partition d'équivalence sur le format email : {valide, sans @,
 *    domaine sans TLD, vide} ;
 *  - Analyse aux limites sur la longueur : 254 caractères (RFC 5321)
 *    = accepté / 255 = refusé ; local-part > 64 caractères = refusé ;
 *  - Table de décision sur le cycle de vie :
 *      R1: première inscription -> 201 persistée
 *      R2: doublon exact -> idempotent (pas de 2e ligne)
 *      R3: casse différente -> même ligne (unicité insensible à la casse)
 *      R4: désinscription par token puis ré-inscription -> réactivée
 *      R5: token inconnu -> 400
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:newsletter;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "wydad.internal-secret=secret-test"
})
@AutoConfigureMockMvc
@Transactional
class NewsletterSecurityTest {

    private static final String BASE = "/api/notification/newsletter";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NewsletterSubscriberRepository repository;

    @BeforeEach
    void cleanBase() {
        repository.deleteAll();
    }

    private String body(String email) {
        return "{\"email\": \"" + email + "\"}";
    }

    // ─── Partition d'équivalence : format email ────────────────────────

    @Test
    @DisplayName("[EP] email valide, inscription ANONYME -> 201 persistée")
    void inscriptionAnonymeValide() throws Exception {
        mockMvc.perform(post(BASE + "/subscribe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("supporter@example.ma")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("supporter@example.ma"));
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("[EP] email sans @ -> 400, rien persisté")
    void emailSansArobaseRefuse() throws Exception {
        mockMvc.perform(post(BASE + "/subscribe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("supporter.example.ma")))
                .andExpect(status().isBadRequest());
        assertThat(repository.count()).isZero();
    }

    @Test
    @DisplayName("[EP] domaine sans TLD -> 400")
    void domaineSansTldRefuse() throws Exception {
        mockMvc.perform(post(BASE + "/subscribe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("supporter@localhost")))
                .andExpect(status().isBadRequest());
        assertThat(repository.count()).isZero();
    }

    @Test
    @DisplayName("[EP] email vide -> 400 (validation jakarta)")
    void emailVideRefuse() throws Exception {
        mockMvc.perform(post(BASE + "/subscribe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("")))
                .andExpect(status().isBadRequest());
        assertThat(repository.count()).isZero();
    }

    // ─── Analyse aux limites : longueur (RFC 5321) ─────────────────────

    @Test
    @DisplayName("[BVA] 254 caractères (limite RFC 5321) accepté -> 201")
    void limite254Acceptee() throws Exception {
        // local-part 60 + @ + domaine construit pour totaliser 254.
        String local = "a".repeat(60);
        String domaine = "b".repeat(250 - local.length()) + ".ma"; // 190 b + .ma
        String email = local + "@" + domaine;
        assertThat(email.length()).isEqualTo(254);

        mockMvc.perform(post(BASE + "/subscribe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(email)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("[BVA] 255 caractères (borne+1) refusé -> 400")
    void limite255Refusee() throws Exception {
        String email = "a".repeat(60) + "@" + "b".repeat(251 - 60) + ".ma";
        assertThat(email.length()).isEqualTo(255);

        mockMvc.perform(post(BASE + "/subscribe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(email)))
                .andExpect(status().isBadRequest());
        assertThat(repository.count()).isZero();
    }

    @Test
    @DisplayName("[BVA] local-part > 64 caractères refusé -> 400")
    void localPartTropLongRefuse() throws Exception {
        String email = "a".repeat(65) + "@example.ma";
        mockMvc.perform(post(BASE + "/subscribe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(email)))
                .andExpect(status().isBadRequest());
        assertThat(repository.count()).isZero();
    }

    // ─── Table de décision : cycle de vie ──────────────────────────────

    @Test
    @DisplayName("[TD-R2] doublon exact -> idempotent, une seule ligne")
    void doublonExactIdempotent() throws Exception {
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post(BASE + "/subscribe")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body("supporter@example.ma")))
                    .andExpect(status().isCreated());
        }
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("[TD-R3] casse différente -> même ligne (unicité insensible à la casse)")
    void uniciteInsensibleCasse() throws Exception {
        mockMvc.perform(post(BASE + "/subscribe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Supporter@Example.MA")))
                .andExpect(status().isCreated());
        mockMvc.perform(post(BASE + "/subscribe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("supporter@example.ma")))
                .andExpect(status().isCreated());
        assertThat(repository.count()).isEqualTo(1);
        assertThat(repository.findAll().get(0).getEmail()).isEqualTo("supporter@example.ma");
    }

    @Test
    @DisplayName("[TD-R4] désinscription par token puis ré-inscription -> réactivée")
    void desinscriptionPuisReinscription() throws Exception {
        mockMvc.perform(post(BASE + "/subscribe")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("supporter@example.ma"))).andExpect(status().isCreated());
        var token = repository.findAll().get(0).getUnsubscribeToken();

        // Désinscription anonyme via le token (jamais l'id séquentiel).
        mockMvc.perform(get(BASE + "/unsubscribe/" + token))
                .andExpect(status().isOk());
        assertThat(repository.findAll().get(0).isActive()).isFalse();

        // Ré-inscription : même ligne réactivée, pas de doublon.
        mockMvc.perform(post(BASE + "/subscribe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("supporter@example.ma")))
                .andExpect(status().isCreated());
        assertThat(repository.count()).isEqualTo(1);
        assertThat(repository.findAll().get(0).isActive()).isTrue();
    }

    @Test
    @DisplayName("[TD-R5] token de désinscription inconnu -> 400")
    void tokenInconnuRefuse() throws Exception {
        mockMvc.perform(get(BASE + "/unsubscribe/token-inexistant"))
                .andExpect(status().isBadRequest());
    }
}
