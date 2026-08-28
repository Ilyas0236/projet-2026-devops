package com.wydad.digital.auth.service.subscription;

import com.wydad.digital.auth.dto.subscription.SubscriptionPlanResponse;
import com.wydad.digital.auth.dto.subscription.SubscriptionPlanUpsertRequest;
import com.wydad.digital.auth.model.subscription.SubscriptionPlan;
import com.wydad.digital.auth.repository.subscription.SubscriptionPlanRepository;
import com.wydad.digital.auth.repository.subscription.UserSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Gestion administrative des plans d'abonnement.
 *
 * Le service NE supprime pas un plan référencé par au moins un
 * {@code UserSubscription} : on renvoie une exception que le contrôleur
 * traduit en 409. Pour "désactiver" un plan, utiliser {@code isActive=false}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionPlanService {

    private final SubscriptionPlanRepository planRepository;
    private final UserSubscriptionRepository subscriptionRepository;

    /** Catalogue public (home + page /abonnement). */
    @Transactional(readOnly = true)
    public List<SubscriptionPlanResponse> listActive() {
        return planRepository.findByIsActiveTrueOrderByDisplayOrderAsc().stream()
                .map(SubscriptionPlanResponse::from)
                .collect(Collectors.toList());
    }

    /** Inventaire admin paginé, filtré par actif/inactif. */
    @Transactional(readOnly = true)
    public Page<SubscriptionPlanResponse> listAll(Boolean isActive, Pageable pageable) {
        Page<SubscriptionPlan> page = (isActive == null)
                ? planRepository.findAll(pageable)
                : planRepository.findByIsActive(isActive, pageable);
        return page.map(SubscriptionPlanResponse::from);
    }

    @Transactional(readOnly = true)
    public SubscriptionPlanResponse getById(Long id) {
        return planRepository.findById(id)
                .map(SubscriptionPlanResponse::from)
                .orElseThrow(() -> new PlanNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public Optional<SubscriptionPlan> findByCode(String code) {
        return planRepository.findByCode(code);
    }

    @Transactional
    public SubscriptionPlanResponse create(SubscriptionPlanUpsertRequest req) {
        if (planRepository.findByCode(req.code()).isPresent()) {
            throw new DuplicatePlanCodeException(req.code());
        }
        SubscriptionPlan p = SubscriptionPlan.builder()
                .code(req.code())
                .name(req.name())
                .regularPrice(req.regularPrice())
                .adherentPrice(req.adherentPrice() == null ? req.regularPrice() : req.adherentPrice())
                .benefits(req.benefits())
                .isActive(req.isActive() == null ? Boolean.TRUE : req.isActive())
                .displayOrder(req.displayOrder() == null ? 0 : req.displayOrder())
                .exceptionalPriority(Boolean.TRUE.equals(req.exceptionalPriority()))
                .season(req.season())
                .build();
        SubscriptionPlan saved = planRepository.save(p);
        log.info("Plan d'abonnement créé : id={}, code={}", saved.getId(), saved.getCode());
        return SubscriptionPlanResponse.from(saved);
    }

    @Transactional
    public SubscriptionPlanResponse update(Long id, SubscriptionPlanUpsertRequest req) {
        SubscriptionPlan p = planRepository.findById(id)
                .orElseThrow(() -> new PlanNotFoundException(id));
        if (planRepository.existsByCodeAndIdNot(req.code(), id)) {
            throw new DuplicatePlanCodeException(req.code());
        }
        p.setCode(req.code());
        p.setName(req.name());
        p.setRegularPrice(req.regularPrice());
        p.setAdherentPrice(req.adherentPrice() == null ? req.regularPrice() : req.adherentPrice());
        p.setBenefits(req.benefits());
        if (req.isActive() != null) p.setIsActive(req.isActive());
        if (req.displayOrder() != null) p.setDisplayOrder(req.displayOrder());
        if (req.exceptionalPriority() != null) p.setExceptionalPriority(req.exceptionalPriority());
        p.setSeason(req.season());
        return SubscriptionPlanResponse.from(planRepository.save(p));
    }

    @Transactional
    public void delete(Long id) {
        if (!planRepository.existsById(id)) {
            throw new PlanNotFoundException(id);
        }
        if (subscriptionRepository.existsByPlan_Id(id)) {
            throw new PlanInUseException(id);
        }
        planRepository.deleteById(id);
        log.info("Plan d'abonnement {} supprimé", id);
    }

    /** Levée quand un plan référencé empêche le delete. → 409. */
    public static class PlanInUseException extends RuntimeException {
        public PlanInUseException(Long id) {
            super("Plan " + id + " référencé par des abonnements existants : désactivez-le (isActive=false) au lieu de le supprimer.");
        }
    }

    /** Levée quand le code est déjà pris (create ou update). → 409. */
    public static class DuplicatePlanCodeException extends RuntimeException {
        public DuplicatePlanCodeException(String code) {
            super("Un plan avec le code '" + code + "' existe déjà.");
        }
    }

    /** Levée quand l'id n'existe pas. → 404. */
    public static class PlanNotFoundException extends RuntimeException {
        public PlanNotFoundException(Long id) {
            super("Plan d'abonnement introuvable : id=" + id);
        }
    }
}
