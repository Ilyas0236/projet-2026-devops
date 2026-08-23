package com.wydad.digital.content.service;

import com.wydad.digital.content.model.Sponsor;
import com.wydad.digital.content.repository.SponsorRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * B.7 — Gestion des sponsors : écriture ADMIN uniquement (la vérification
 * de rôle est faite au contrôleur via @PreAuthorize), lecture publique.
 */
@Service
@RequiredArgsConstructor
public class SponsorService {

    private final SponsorRepository sponsorRepository;

    @Transactional(readOnly = true)
    public List<Sponsor> getPublicSponsors() {
        return sponsorRepository.findByActiveTrueOrderByDisplayOrderAsc();
    }

    @Transactional(readOnly = true)
    public List<Sponsor> getAllSponsors() {
        return sponsorRepository.findAllByOrderByDisplayOrderAsc();
    }

    @Transactional
    public Sponsor create(String name, String logoUrl, String websiteUrl, String tier, Integer displayOrder) {
        validate(name, logoUrl, tier);
        return sponsorRepository.save(Sponsor.builder()
                .name(name.trim())
                .logoUrl(logoUrl.trim())
                .websiteUrl(websiteUrl != null && !websiteUrl.isBlank() ? websiteUrl.trim() : null)
                .tier(tier.trim())
                .displayOrder(displayOrder != null ? displayOrder : 0)
                .build());
    }

    @Transactional
    public Sponsor update(Long id, String name, String logoUrl, String websiteUrl,
                          String tier, Integer displayOrder, Boolean active) {
        Sponsor s = sponsorRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Sponsor non trouvé: " + id));
        if (name != null && !name.isBlank()) s.setName(name.trim());
        if (logoUrl != null && !logoUrl.isBlank()) s.setLogoUrl(logoUrl.trim());
        if (websiteUrl != null) s.setWebsiteUrl(websiteUrl.isBlank() ? null : websiteUrl.trim());
        if (tier != null && !tier.isBlank()) s.setTier(tier.trim());
        if (displayOrder != null) s.setDisplayOrder(displayOrder);
        if (active != null) s.setActive(active);
        return sponsorRepository.save(s);
    }

    @Transactional
    public void delete(Long id) {
        if (!sponsorRepository.existsById(id)) {
            throw new EntityNotFoundException("Sponsor non trouvé: " + id);
        }
        sponsorRepository.deleteById(id);
    }

    private void validate(String name, String logoUrl, String tier) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Le nom du sponsor est obligatoire");
        }
        if (logoUrl == null || logoUrl.isBlank()) {
            throw new IllegalArgumentException("L'URL du logo est obligatoire");
        }
        if (tier == null || tier.isBlank()) {
            throw new IllegalArgumentException("Le niveau de partenariat est obligatoire");
        }
    }
}
