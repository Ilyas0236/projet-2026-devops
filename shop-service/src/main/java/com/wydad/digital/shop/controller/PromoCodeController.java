package com.wydad.digital.shop.controller;

import com.wydad.digital.shop.dto.PromoCodeDto;
import com.wydad.digital.shop.model.PromoCode;
import com.wydad.digital.shop.repository.PromoCodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * B.12.b — Gestion des codes promo par l'ADMIN, directement depuis l'UI :
 * plus aucune intervention base de données nécessaire pour lancer une
 * promotion (la remise est appliquée et plafonnée serveur dans OrderService).
 */
@RestController
@RequestMapping("/api/shop/promo-codes")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class PromoCodeController {

    private final PromoCodeRepository promoCodeRepository;

    @GetMapping
    public ResponseEntity<List<PromoCodeDto>> getAll() {
        return ResponseEntity.ok(promoCodeRepository.findAll().stream()
                .map(this::toDto).toList());
    }

    @PostMapping
    public ResponseEntity<PromoCodeDto> create(@RequestBody PromoCodeRequest request) {
        String code = request.getCode().trim().toUpperCase(Locale.ROOT);
        if (promoCodeRepository.findByCode(code).isPresent()) {
            throw new IllegalArgumentException("Un code promo avec ce code existe déjà");
        }
        if (request.getDiscountPercent() == null || request.getDiscountPercent().signum() <= 0
                || request.getDiscountPercent().compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("La remise doit être un pourcentage entre 1 et 100");
        }
        PromoCode promo = PromoCode.builder()
                .code(code)
                .description(request.getDescription())
                .discountPercent(request.getDiscountPercent())
                .maxDiscountAmount(request.getMaxDiscountAmount())
                .minOrderAmount(request.getMinOrderAmount())
                .maxUses(request.getMaxUses())
                .currentUses(0)
                .validFrom(request.getValidFrom())
                .validUntil(request.getValidUntil())
                .active(true)
                .build();
        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(promoCodeRepository.save(promo)));
    }

    /** Activation / désactivation : un code désactivé n'est plus applicable. */
    @PatchMapping("/{id}/active")
    public ResponseEntity<PromoCodeDto> setActive(@PathVariable Long id,
                                                  @RequestBody SetActiveRequest body) {
        PromoCode promo = promoCodeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Code promo introuvable"));
        if (body.active() == null) {
            throw new IllegalArgumentException("Le champ 'active' est obligatoire");
        }
        promo.setActive(body.active());
        return ResponseEntity.ok(toDto(promoCodeRepository.save(promo)));
    }

    @lombok.Data
    public static class PromoCodeRequest {
        public String code;
        public String description;
        public BigDecimal discountPercent;
        public BigDecimal maxDiscountAmount;
        public BigDecimal minOrderAmount;
        public Integer maxUses;
        public LocalDateTime validFrom;
        public LocalDateTime validUntil;
    }

    public record SetActiveRequest(Boolean active) {
    }

    private PromoCodeDto toDto(PromoCode p) {
        return PromoCodeDto.builder()
                .id(p.getId())
                .code(p.getCode())
                .description(p.getDescription())
                .discountPercent(p.getDiscountPercent())
                .maxDiscountAmount(p.getMaxDiscountAmount())
                .minOrderAmount(p.getMinOrderAmount())
                .maxUses(p.getMaxUses())
                .currentUses(p.getCurrentUses())
                .validFrom(p.getValidFrom())
                .validUntil(p.getValidUntil())
                .active(p.getActive())
                .build();
    }
}
