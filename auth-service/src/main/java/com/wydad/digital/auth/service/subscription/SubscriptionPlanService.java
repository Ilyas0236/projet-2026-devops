package com.wydad.digital.auth.service.subscription;

import com.wydad.digital.auth.dto.subscription.SubscriptionPlanResponse;
import com.wydad.digital.auth.dto.subscription.SubscriptionPlanUpsertRequest;
import com.wydad.digital.auth.model.subscription.SubscriptionPlan;
import com.wydad.digital.auth.repository.subscription.SubscriptionPlanRepository;
import com.wydad.digital.auth.repository.subscription.UserSubscriptionRepository;
import com.wydad.digital.auth.service.CloudinaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
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
    private final CloudinaryService cloudinaryService;

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

    /**
     * Upload (ou remplacement) de la photo de la carte d'un plan.
     * Le fichier part sur Cloudinary (type=upload public) et l'URL sécurisée
     * est stockée dans {@code plan.cardImageUrl}.
     *
     * <p>Endpoint séparé de l'upsert JSON pour éviter de mélanger multipart
     * et JSON dans la même requête. Si Cloudinary n'est pas configuré (mode
     * dégradé local), l'URL reste null et le front affichera le bandeau
     * sans photo — mais le circuit admin reste fonctionnel.</p>
     */
    @Transactional
    public SubscriptionPlanResponse setCardImage(Long id, MultipartFile file) throws IOException {
        SubscriptionPlan p = planRepository.findById(id)
                .orElseThrow(() -> new PlanNotFoundException(id));
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Fichier vide.");
        }
        CloudinaryService.UploadResult result = cloudinaryService.uploadPlanCardImage(file, p.getCode());
        // En mode dégradé sans clés Cloudinary, secureUrl est null : on n'écrit
        // pas l'URL en BDD pour ne pas stocker un placeholder.
        if (result.secureUrl() != null) {
            p.setCardImageUrl(result.secureUrl());
        } else {
            log.warn("Cloudinary non configuré : cardImageUrl non persisté pour le plan {}", p.getCode());
        }
        SubscriptionPlan saved = planRepository.save(p);
        log.info("Photo de carte mise à jour pour le plan {} (id={})", saved.getCode(), saved.getId());
        return SubscriptionPlanResponse.from(saved);
    }

    /**
     * Supprime l'URL de la photo de carte en BDD. L'image Cloudinary
     * n'est PAS détruite côté Cloudinary (sera nettoyée par le job de
     * maintenance Cloudinary si on en installe un — pragmatique pour V1).
     */
    @Transactional
    public SubscriptionPlanResponse clearCardImage(Long id) {
        SubscriptionPlan p = planRepository.findById(id)
                .orElseThrow(() -> new PlanNotFoundException(id));
        p.setCardImageUrl(null);
        SubscriptionPlan saved = planRepository.save(p);
        log.info("Photo de carte retirée pour le plan {} (id={})", saved.getCode(), saved.getId());
        return SubscriptionPlanResponse.from(saved);
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
