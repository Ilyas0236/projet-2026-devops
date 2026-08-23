package com.wydad.digital.gateway.filter;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 0-BIS.2 — Aucun endpoint /api/{service}/internal/** ne doit etre joignable
 * depuis l'exterieur. Deux niveaux de verification :
 * - fonctionnel : chaque service route par la gateway repond 403 sur son
 *   chemin interne (avec un JWT valide — sans JWT le filtre global repond
 *   deja 401 avant meme d'atteindre la route de blocage) ;
 * - structurel : toute route de service ajoutee a l'avenir doit posseder sa
 *   route block-*-internal, sinon ce test echoue au moment de l'ajout.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "jwt.secret=cle-de-test-hmac-256-bits-tres-exactement-32-octets!")
class InternalRoutesBlockedTest {

    /** Tous les services declares dans application.yml. */
    private static final List<String> ROUTED_SERVICES =
            List.of("auth", "content", "payment", "shop", "ticket", "sports", "gamification", "notification");

    private static final String SECRET = "cle-de-test-hmac-256-bits-tres-exactement-32-octets!";

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private RouteDefinitionLocator routeDefinitionLocator;

    private String validJwt() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject("fan@wydad.ma")
                .claim("role", "FAN")
                .claim("id", 1L)
                .signWith(key)
                .compact();
    }

    @Test
    void internalEndpointRenvoie403MemeAvecJwtValide() {
        for (String service : ROUTED_SERVICES) {
            webTestClient.get()
                    .uri("/api/" + service + "/internal/ping")
                    .header("Authorization", "Bearer " + validJwt())
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus().isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    @Test
    void internalEndpointJamaisProxyfieSansToken() {
        for (String service : ROUTED_SERVICES) {
            // Sans JWT le filtre global coupe en 401 avant la route de blocage ;
            // dans tous les cas la requete ne doit jamais atteindre le backend
            // (ni 200 ni 404 de service).
            webTestClient.get()
                    .uri("/api/" + service + "/internal/ping")
                    .exchange()
                    .expectStatus().value(status ->
                            assertTrue(status == HttpStatus.UNAUTHORIZED.value()
                                            || status == HttpStatus.FORBIDDEN.value(),
                                    "/api/" + service + "/internal/** devrait etre bloque (401/403), recu " + status));
        }
    }

    @Test
    void toutServiceRoutePossedeSaRouteDeBlocageInterne() {
        List<RouteDefinition> routes = routeDefinitionLocator.getRouteDefinitions().collectList().block();

        Set<String> servicesRoutes = new HashSet<>();
        Set<String> servicesBloques = new HashSet<>();
        for (RouteDefinition route : routes) {
            String path = route.getPredicates().stream()
                    .filter(p -> "Path".equals(p.getName()))
                    .map(p -> String.valueOf(p.getArgs().values().iterator().next()))
                    .findFirst().orElse("");
            var matcher = java.util.regex.Pattern.compile("^/api/([a-z]+)/").matcher(path);
            if (!matcher.find()) {
                continue;
            }
            String service = matcher.group(1);
            if (route.getId().startsWith("block-") && route.getId().endsWith("-internal")) {
                servicesBloques.add(service);
            } else {
                servicesRoutes.add(service);
            }
        }

        assertEquals(servicesRoutes, servicesBloques,
                "Services routes sans blocage /internal/** correspondant");
        assertTrue(servicesRoutes.containsAll(ROUTED_SERVICES),
                "La liste ROUTED_SERVICES du test doit couvrir tous les services de application.yml");
    }
}
