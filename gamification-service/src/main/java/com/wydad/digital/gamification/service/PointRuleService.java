package com.wydad.digital.gamification.service;

import com.wydad.digital.gamification.model.PointRule;
import com.wydad.digital.gamification.repository.PointRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * B.8 — Barème de points paramétrable.
 *
 * <p>Seed initial au démarrage (idempotent) + cache mémoire pour éviter
 * un round-trip DB sur chaque pronostic. Recharge explicite après update
 * admin via {@link #refreshCache()}.</p>
 *
 * <p>Le seed tourne sur {@link ApplicationReadyEvent} plutôt qu'en
 * {@code @PostConstruct} : à ce stade, Hibernate a déjà créé toutes les
 * tables JPA (sinon en H2/test on tombe sur "Table POINT_RULES non trouvée"
 * car la DDL est appliquée après l'init des beans).</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PointRuleService {

    private final PointRuleRepository pointRuleRepository;

    /** Cache simple (key → value). */
    private final java.util.Map<String, Integer> cache = new java.util.concurrent.ConcurrentHashMap<>();

    /** Valeurs par défaut (utilisées au seed et comme fallback si DB vide). */
    public static final String KEY_PREDICTION_BONUS = "prediction.bonus";
    public static final String KEY_PREDICTION_EXACT = "prediction.exact_score";
    public static final String KEY_PREDICTION_SIGN = "prediction.bon_signe";
    public static final String KEY_LEVEL_POINTS = "level.points_per_level";

    /** Seed + première mise en cache, à la fin de l'init Spring. */
    @EventListener(ApplicationReadyEvent.class)
    public void onAppReady() {
        try {
            seedIfEmpty();
            refreshCache();
        } catch (RuntimeException e) {
            // En test H2 (DDL lazy), la table n'existe pas encore — on retente
            // à la prochaine lecture, jamais à chaud en prod.
            log.warn("B.8 — seed/refresh impossible au boot, retry à la 1ère lecture: {}", e.getMessage());
        }
    }

    private void seedIfEmpty() {
        long count = pointRuleRepository.count();
        if (count == 0) {
            log.info("B.8 — seed initial de 4 règles de points");
            pointRuleRepository.save(PointRule.builder()
                    .ruleKey(KEY_PREDICTION_BONUS)
                    .label("Pronostic soumis (bonus de participation)")
                    .description("Points crédités à un membre dès qu'il soumet un pronostic, quel que soit le résultat.")
                    .value(10).build());
            pointRuleRepository.save(PointRule.builder()
                    .ruleKey(KEY_PREDICTION_EXACT)
                    .label("Score exact")
                    .description("Points crédités quand le pronostic matche exactement le score final du match.")
                    .value(25).build());
            pointRuleRepository.save(PointRule.builder()
                    .ruleKey(KEY_PREDICTION_SIGN)
                    .label("Bon signe (victoire/nul/défaite)")
                    .description("Points crédités quand le signe 1X2 est correct mais le score est inexact.")
                    .value(10).build());
            pointRuleRepository.save(PointRule.builder()
                    .ruleKey(KEY_LEVEL_POINTS)
                    .label("Points par niveau")
                    .description("Nombre de points cumulés nécessaires pour passer au niveau suivant.")
                    .value(500).build());
        }
    }

    /** Recharge le cache depuis la DB (à appeler après chaque update admin). */
    @Transactional(readOnly = true)
    public void refreshCache() {
        cache.clear();
        pointRuleRepository.findAll().forEach(r -> cache.put(r.getRuleKey(), r.getValue()));
        log.info("B.8 — cache barème rafraîchi ({} règles)", cache.size());
    }

    /**
     * Lecture d'une règle. Si le cache n'a pas encore été initialisé, on
     * tente un refresh paresseux (cas d'un 1er accès avant que
     * {@link #onAppReady()} n'ait pu se terminer — typique en test H2 où
     * la DDL est paresseuse). Si la table est toujours indisponible, on
     * retombe silencieusement sur la valeur par défaut plutôt que d'échouer.
     */
    public int get(String key, int defaultValue) {
        if (cache.isEmpty()) {
            try {
                refreshCache();
            } catch (RuntimeException ignored) {
                // Cache toujours vide → on rend la valeur par défaut
                // plutôt que de planter le boot.
            }
        }
        Integer v = cache.get(key);
        return v != null ? v : defaultValue;
    }

    @Transactional(readOnly = true)
    public List<PointRule> getAll() {
        return pointRuleRepository.findAllByOrderByRuleKeyAsc();
    }

    /**
     * Update d'une règle. Refuse les valeurs négatives sur les points unitaires
     * (le level.points_per_level peut être abaissé temporairement, jamais à 0).
     */
    @Transactional
    public PointRule update(String key, Integer newValue) {
        PointRule r = pointRuleRepository.findByRuleKey(key)
                .orElseThrow(() -> new IllegalArgumentException("Règle inconnue: " + key));
        if (newValue == null) {
            throw new IllegalArgumentException("La valeur est obligatoire");
        }
        if (newValue < 0 && !KEY_LEVEL_POINTS.equals(key)) {
            throw new IllegalArgumentException("Les points unitaires ne peuvent pas être négatifs");
        }
        if (KEY_LEVEL_POINTS.equals(key) && newValue < 50) {
            throw new IllegalArgumentException("Le seuil de niveau doit être ≥ 50 points");
        }
        r.setValue(newValue);
        PointRule saved = pointRuleRepository.save(r);
        refreshCache();
        log.info("B.8 — règle '{}' mise à jour → {}", key, newValue);
        return saved;
    }
}
