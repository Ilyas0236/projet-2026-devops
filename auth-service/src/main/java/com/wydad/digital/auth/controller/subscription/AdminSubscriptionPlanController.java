package com.wydad.digital.auth.controller.subscription;

import com.wydad.digital.auth.dto.subscription.SubscriptionPlanResponse;
import com.wydad.digital.auth.dto.subscription.SubscriptionPlanUpsertRequest;
import com.wydad.digital.auth.service.subscription.SubscriptionPlanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * CRUD admin des plans d'abonnement saisonnier — préfixe {@code /api/admin}
 * pour rester aligné avec les autres panels admin (auth, contenu, etc.).
 * La protection est double :
 *  - @PreAuthorize côté Spring Security
 *  - adminGuard côté front (Angular)
 */
@RestController
@RequestMapping("/api/admin/subscription-plans")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminSubscriptionPlanController {

    private final SubscriptionPlanService planService;

    @GetMapping
    public ResponseEntity<Page<SubscriptionPlanResponse>> list(
            @RequestParam(value = "active", required = false) Boolean active,
            Pageable pageable) {
        return ResponseEntity.ok(planService.listAll(active, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SubscriptionPlanResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(planService.getById(id));
    }

    @PostMapping
    public ResponseEntity<SubscriptionPlanResponse> create(
            @Valid @RequestBody SubscriptionPlanUpsertRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(planService.create(req));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SubscriptionPlanResponse> update(
            @PathVariable Long id, @Valid @RequestBody SubscriptionPlanUpsertRequest req) {
        return ResponseEntity.ok(planService.update(id, req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        planService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
