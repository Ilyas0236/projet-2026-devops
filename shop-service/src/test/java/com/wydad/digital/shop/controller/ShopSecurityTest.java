package com.wydad.digital.shop.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * B.12.a — Le panier est strictement personnel : toutes ses routes, y
 * compris la lecture, exigent un rôle membre (ADHERENT ou ADMIN) ET une
 * identité posée par la gateway. Un en-tête X-User-Email seul ne suffit
 * jamais : sans rôle membre -> 403, même authentifié.
 *
 * H2 en mode compatibilité PostgreSQL ; revalidation sur PostgreSQL au déploiement.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:shopsecurity;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        // L'application.yml force le dialecte PostgreSQL : on le remplace pour H2.
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@AutoConfigureMockMvc
@Transactional
class ShopSecurityTest {

    private static final String BASE = "/api/shop";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /cart avec seulement X-User-Email (sans rôle membre) -> 403")
    void lireLePanierSansRoleMembreRefuse() throws Exception {
        mockMvc.perform(get(BASE + "/cart").header("X-User-Email", "fan@wydad.ma"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /cart sans aucune identité -> 403")
    void lireLePanierAnonymeRefuse() throws Exception {
        mockMvc.perform(get(BASE + "/cart"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /cart ADHERENT identifié par la gateway -> 200")
    void lireLePanierAdherentAutorise() throws Exception {
        mockMvc.perform(get(BASE + "/cart")
                        .header("X-User-Id", "42")
                        .header("X-User-Email", "fan@wydad.ma")
                        .header("X-User-Role", "ADHERENT"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Un STAFF n'est pas membre : GET /cart -> 403")
    void staffNEstPasMembreDuPanier() throws Exception {
        mockMvc.perform(get(BASE + "/cart")
                        .header("X-User-Id", "7")
                        .header("X-User-Email", "staff@wydad.ma")
                        .header("X-User-Role", "STAFF"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Créer / modifier / supprimer un produit exige ADMIN")
    void mutationsProduitExigentAdmin() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post(BASE + "/products")
                        .header("X-User-Id", "42")
                        .header("X-User-Email", "fan@wydad.ma")
                        .header("X-User-Role", "ADHERENT")
                        .contentType("application/json")
                        .content("{\"name\":\"Fraude\",\"basePrice\":1,\"sportSection\":\"FOOTBALL\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete(BASE + "/products/1")
                        .header("X-User-Id", "42")
                        .header("X-User-Email", "fan@wydad.ma")
                        .header("X-User-Role", "ADHERENT"))
                .andExpect(status().isForbidden());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch(BASE + "/orders/FAKE-1/status") // commande inexistante mais le refus est AVANT (403)
                        .header("X-User-Id", "42")
                        .header("X-User-Email", "fan@wydad.ma")
                        .header("X-User-Role", "ADHERENT")
                        .contentType("application/json")
                        .content("{\"status\":\"SHIPPED\"}"))
                .andExpect(status().isForbidden());
    }
}
