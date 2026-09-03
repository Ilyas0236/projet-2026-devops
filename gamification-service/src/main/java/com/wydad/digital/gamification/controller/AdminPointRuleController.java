package com.wydad.digital.gamification.controller;

import com.wydad.digital.gamification.model.PointRule;
import com.wydad.digital.gamification.service.PointRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * B.8 — Pilotage admin du barème de points.
 *
 * GET : liste toutes les règles (lecture).
 * PUT : met à jour une règle (le label/description ne sont pas modifiables
 *       depuis cette API pour éviter qu'un admin les traduise mal — ils
 *       restent gérés dans le code si on doit les faire évoluer).
 */
@RestController
@RequestMapping("/api/gamification/admin/point-rules")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminPointRuleController {

    private final PointRuleService pointRuleService;

    @GetMapping
    public List<PointRule> list() {
        return pointRuleService.getAll();
    }

    /** Update partiel : { "value": 30 } suffit. */
    @PutMapping("/{key}")
    public PointRule update(@PathVariable String key, @RequestBody Map<String, Integer> body) {
        Integer value = body.get("value");
        return pointRuleService.update(key, value);
    }
}
