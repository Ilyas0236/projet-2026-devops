package com.wydad.digital.gamification.bootstrap;

import com.wydad.digital.gamification.model.BadgeDefinition;
import com.wydad.digital.gamification.repository.BadgeDefinitionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * B.8 — Seed initial des badges de fidélité.
 *
 * <p>Insère 5 badges par défaut à la première installation, idempotent via
 * {@code findByCode(...).isEmpty()}. Les seuils {@code minPoints} déclenchent
 * l'attribution automatique dès que le solde de points d'un supporter les
 * franchit (cf. {@code BadgeService.awardEligibleBadges}).</p>
 *
 * <p>Le seed tourne sur {@link ApplicationReadyEvent} plutôt qu'en
 * {@code @PostConstruct} : à ce stade, Hibernate a déjà créé toutes les
 * tables JPA (en H2/test la DDL est paresseuse et {@code @PostConstruct}
 * plante sur "Table BADGE_DEFINITIONS non trouvée"). Cf. même pattern dans
 * {@code PointRuleService}.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BadgeSeeder {

    private final BadgeDefinitionRepository badgeRepository;

    /** Codes stables — préfixés "WAC_" pour éviter toute collision avec des
     *  codes de test ou de futures migrations. NE PAS modifier sans migration
     *  (clé métier implicite dans user_badges). */
    public static final String CODE_ROOKIE = "WAC_ROOKIE";
    public static final String CODE_SUPPORTER = "WAC_SUPPORTER";
    public static final String CODE_CENTURION = "WAC_CENTURION";
    public static final String CODE_VETERAN = "WAC_VETERAN";
    public static final String CODE_LEGEND = "WAC_LEGEND";

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void onAppReady() {
        try {
            seedIfEmpty();
        } catch (RuntimeException e) {
            // En test H2 (DDL lazy), la table peut être indisponible — on retente
            // au prochain accès, jamais à chaud en prod.
            log.warn("B.8 — seed badges impossible au boot, retry à la 1ère lecture: {}", e.getMessage());
        }
    }

    private void seedIfEmpty() {
        if (badgeRepository.count() > 0) {
            log.debug("B.8 — seed badges ignoré (catalogue non vide)");
            return;
        }
        log.info("B.8 — seed initial de 5 badges de fidélité");

        List<BadgeDefinition> defaults = List.of(
                BadgeDefinition.builder()
                        .code(CODE_ROOKIE)
                        .name("Rookie")
                        .description("Bienvenue chez les Wydadis ! Soumettez votre premier pronostic pour débloquer ce badge.")
                        .minPoints(0) // attribué dès la 1ère action (voir awardEligibleBadges — déclencheur event, pas seuil)
                        .active(true)
                        .build(),
                BadgeDefinition.builder()
                        .code(CODE_SUPPORTER)
                        .name("Supporter")
                        .description("Vous avez franchi le cap des 25 points. Le maillot vous va comme un gant !")
                        .minPoints(25)
                        .active(true)
                        .build(),
                BadgeDefinition.builder()
                        .code(CODE_CENTURION)
                        .name("Centurion")
                        .description("100 points au compteur — vous êtes un pilier des Tribune Wydad.")
                        .minPoints(100)
                        .active(true)
                        .build(),
                BadgeDefinition.builder()
                        .code(CODE_VETERAN)
                        .name("Vétéran")
                        .description("250 points : vous avez vécu les heures chaudes du championnat. Respect éternel.")
                        .minPoints(250)
                        .active(true)
                        .build(),
                BadgeDefinition.builder()
                        .code(CODE_LEGEND)
                        .name("Légende")
                        .description("500 points. Vous entrez dans le panthéon des supporters Wydad. Vous êtes la légende.")
                        .minPoints(500)
                        .active(true)
                        .build()
        );

        // Idempotence par code (la table a une contrainte UNIQUE sur code,
        // donc on protège aussi des inserts en double si count()==0 mais que
        // la séquence a été avancée entre temps).
        for (BadgeDefinition def : defaults) {
            if (badgeRepository.findByCode(def.getCode()).isEmpty()) {
                badgeRepository.save(def);
            }
        }
        log.info("B.8 — seed badges terminé ({} entrées)", badgeRepository.count());
    }
}
