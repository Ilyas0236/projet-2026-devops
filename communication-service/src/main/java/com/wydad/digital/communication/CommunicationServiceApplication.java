package com.wydad.digital.communication;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Communication-service — messagerie joueur ↔ staff, annonces du club,
 * chat de groupe temps réel (STOMP). Audit thématique : ces fonctions
 * sont de la COMMUNICATION, pas de l'opérationnel sportif ; elles
 * quittent sports-service pour respecter « un service = une responsabilité ».
 */
@SpringBootApplication
public class CommunicationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CommunicationServiceApplication.class, args);
    }
}
