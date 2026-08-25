package com.wydad.digital.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Catalogues publics — le filtre JWT global doit laisser passer SANS token :
 * - sondages actifs + resultats publies (exigence B.8, election-service) ;
 * - catalogue boutique et evenements billetterie (pages publiques du front).
 * Les services revalident eux-memes via permitAll cote SecurityConfig ; la
 * gateway ne fait que ne pas exiger le JWT sur ces lecture-là.
 *
 * Dans ce test aucun backend n'est demarre : si le filtre laisse passer,
 * la requete est proxifiee et echoue en 500 (connexion refusee) — JAMAIS
 * en 401, qui signifierait que le filtre bloque a nouveau la lecture publique.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "jwt.secret=cle-de-test-hmac-256-bits-tres-exactement-32-octets!")
class PublicCatalogAccessTest {

    private static final String[] PUBLIC_GET_PATHS = {
            "/api/polls/active",
            "/api/elections/published/latest",
            "/api/shop/products",
            "/api/shop/products/1",
            "/api/ticket/events",
            "/api/ticket/events/upcoming",
    };

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void lecturePubliqueSansTokenNestJamaisBloqueeParLeFiltreJwt() {
        for (String path : PUBLIC_GET_PATHS) {
            webTestClient.get()
                    .uri(path)
                    .exchange()
                    .expectStatus().value(status ->
                            assertTrue(status != HttpStatus.UNAUTHORIZED.value(),
                                    path + " devrait etre accessible sans JWT (filtre global), recu 401"));
        }
    }

    @Test
    void ecritureSurCatalogueResteAuthentifiee() {
        // POST /api/shop/products n'est PAS dans la derogation : sans JWT -> 401.
        webTestClient.post()
                .uri("/api/shop/products")
                .header("Content-Type", "application/json")
                .bodyValue("{}")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private static void assertTrue(boolean condition, String message) {
        org.junit.jupiter.api.Assertions.assertTrue(condition, message);
    }
}
