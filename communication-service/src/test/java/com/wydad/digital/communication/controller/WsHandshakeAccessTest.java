package com.wydad.digital.communication.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Non-régression du 25/08 (campagne de re-test) : le handshake SockJS
 * (/ws/team-chat/**, dont /info interrogé par le client sockjs avant
 * l'upgrade WebSocket) doit être accessible SANS identité gateway — un
 * navigateur ne peut pas poser de header Authorization sur un upgrade WS.
 *
 * <p>Le JWT est validé à la frame CONNECT STOMP par TeamChatAuthInterceptor.
 * Avant correctif, SecurityConfig exigeait anyRequest().authenticated()
 * après la migration depuis sports-service : le /info répondait 403 et
 * AUCUN client ne pouvait ouvrir le chat en prod.</p>
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:wshandshake;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@AutoConfigureMockMvc
class WsHandshakeAccessTest {

    @Autowired MockMvc mvc;

    @Test
    void handshakeSockJSInfoSansIdentiteNestJamais403() throws Exception {
        // Le /info SockJS est la première requête du client ; sans la
        // dérogation SecurityConfig il tombait en 403 anonyme. En MockMvc
        // le endpoint SockJS renvoie 200 (JSON info) — l'essentiel est que
        // ce ne soit PAS un 403 de Spring Security.
        mvc.perform(get("/ws/team-chat/info?t=123456"))
                .andExpect(status().isOk());
    }
}
