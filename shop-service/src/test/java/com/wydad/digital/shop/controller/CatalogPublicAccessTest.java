package com.wydad.digital.shop.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * C1 — Décorticage front ↔ back : le menu public expose « Boutique » à un
 * visiteur anonyme, mais GET /api/shop/products était derrière
 * anyRequest().authenticated() -> 403 pour le fan non connecté.
 *
 * Correction : catalogue produits en lecture publique (donnée non
 * personnelle). Table de décision ISTQB (rôle × route) :
 *
 *   Rôle \ Route        | GET /products | POST /products | GET /cart
 *   --------------------|---------------|----------------|-----------
 *   Anonyme             | 200 (NOUVEAU) |      403       |    403
 *   ADHERENT            | 200           |      403       |    200
 *   ADMIN               | 200           |      201       |    200
 *
 * Les écritures et le panier restent strictement réservés.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:catalogpublic;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@AutoConfigureMockMvc
@Transactional
class CatalogPublicAccessTest {

    private static final String BASE = "/api/shop";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("[TD] Anonyme lit le catalogue -> 200 (page /boutique publique)")
    void anonymeLitCatalogue() throws Exception {
        mockMvc.perform(get(BASE + "/products"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("[TD] Anonyme lit un produit par id -> 404 (route publique, ressource absente) mais PAS 403")
    void anonymeProduitInexistant() throws Exception {
        mockMvc.perform(get(BASE + "/products/999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("[TD] Écriture anonyme toujours refusée -> 403")
    void anonymeEcritureRefusee() throws Exception {
        mockMvc.perform(post(BASE + "/products")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("[TD] Panier anonyme toujours refusé -> 403")
    void anonymePanierRefuse() throws Exception {
        mockMvc.perform(get(BASE + "/cart"))
                .andExpect(status().isForbidden());
    }
}
