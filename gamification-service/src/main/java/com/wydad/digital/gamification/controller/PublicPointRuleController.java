package com.wydad.digital.gamification.controller;

import com.wydad.digital.gamification.service.PointRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * B.8.f — Endpoint PUBLIC pour exposer le barème de points aux membres
 * côté espace-fan. Sans auth, sans token. Renvoie uniquement les règles
 * utiles au front (pas les level.points_per_level qui sont du détail
 * d'implémentation).
 *
 * <p>Pourquoi public : l'encart pédagogique « Qu'est-ce qui me rapporte
 * des points ? » dans espace-fan.component.html doit refléter les
 * réglages admin en temps réel. Avant, les valeurs étaient hard-codées
 * (+10, +25, +10) et devenaient fausses si l'admin touchait au barème.</p>
 */
@RestController
@RequestMapping("/api/gamification/point-rules")
@RequiredArgsConstructor
public class PublicPointRuleController {

    private final PointRuleService pointRuleService;

    /**
     * Renvoie les 3 règles visibles côté fan (pas le level.points_per_level).
     * Format : { predictionBonus: 10, predictionExact: 25, predictionSign: 10 }
     * pour un accès direct par clé dans le front.
     */
    @GetMapping("/public")
    public Map<String, Object> publicRules() {
        return Map.of(
            "predictionBonus", pointRuleService.get(PointRuleService.KEY_PREDICTION_BONUS, 10),
            "predictionExact", pointRuleService.get(PointRuleService.KEY_PREDICTION_EXACT, 25),
            "predictionSign",  pointRuleService.get(PointRuleService.KEY_PREDICTION_SIGN, 10)
        );
    }

    /** Variante liste (utile pour debug / inspection). */
    @GetMapping("/public/all")
    public List<Map<String, Object>> publicAll() {
        return pointRuleService.getAll().stream()
            .filter(r -> !PointRuleService.KEY_LEVEL_POINTS.equals(r.getRuleKey()))
            .map(r -> Map.<String, Object>of(
                "ruleKey", r.getRuleKey(),
                "value", r.getValue(),
                "label", r.getLabel()))
            .toList();
    }
}
