package com.wydad.digital.election;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * election-service — gouvernance du club : élections du président.
 *
 * Premier scheduler du projet (@EnableScheduling) : clôture automatique
 * des élections à leur date de fin, avec calcul et publication des
 * résultats (gagnant + pourcentages).
 */
@SpringBootApplication
@EnableScheduling
public class ElectionServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ElectionServiceApplication.class, args);
    }
}
