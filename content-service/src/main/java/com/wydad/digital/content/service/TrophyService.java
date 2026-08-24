package com.wydad.digital.content.service;

import com.wydad.digital.content.model.Trophy;
import com.wydad.digital.content.repository.TrophyRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Palmarès du club : écriture ADMIN uniquement (vérification de rôle au
 * contrôleur via @PreAuthorize), lecture publique. La validation métier
 * (champs obligatoires, count >= 1) est testée par TrophySecurityTest.
 */
@Service
@RequiredArgsConstructor
public class TrophyService {

    private final TrophyRepository trophyRepository;

    @Transactional(readOnly = true)
    public List<Trophy> getPublicTrophies() {
        return trophyRepository.findByActiveTrueOrderByDisplayOrderAsc();
    }

    @Transactional(readOnly = true)
    public List<Trophy> getAllTrophies() {
        return trophyRepository.findAllByOrderByDisplayOrderAsc();
    }

    @Transactional
    public Trophy create(String title, String category, String season, Integer count,
                         String imageUrl, Integer displayOrder) {
        validate(title, category, season, count);
        return trophyRepository.save(Trophy.builder()
                .title(title.trim())
                .category(category.trim())
                .season(season.trim())
                .count(count != null ? count : 1)
                .imageUrl(imageUrl != null && !imageUrl.isBlank() ? imageUrl.trim() : null)
                .displayOrder(displayOrder != null ? displayOrder : 0)
                .build());
    }

    @Transactional
    public Trophy update(Long id, String title, String category, String season, Integer count,
                         String imageUrl, Integer displayOrder, Boolean active) {
        Trophy t = trophyRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Trophée non trouvé: " + id));
        if (title != null && !title.isBlank()) t.setTitle(title.trim());
        if (category != null && !category.isBlank()) t.setCategory(category.trim());
        if (season != null && !season.isBlank()) t.setSeason(season.trim());
        if (count != null) {
            if (count < 1) {
                throw new IllegalArgumentException("Le nombre de titres doit être >= 1");
            }
            t.setCount(count);
        }
        if (imageUrl != null) t.setImageUrl(imageUrl.isBlank() ? null : imageUrl.trim());
        if (displayOrder != null) t.setDisplayOrder(displayOrder);
        if (active != null) t.setActive(active);
        return trophyRepository.save(t);
    }

    @Transactional
    public void delete(Long id) {
        if (!trophyRepository.existsById(id)) {
            throw new EntityNotFoundException("Trophée non trouvé: " + id);
        }
        trophyRepository.deleteById(id);
    }

    private void validate(String title, String category, String season, Integer count) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("L'intitulé du trophée est obligatoire");
        }
        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("La catégorie est obligatoire");
        }
        if (season == null || season.isBlank()) {
            throw new IllegalArgumentException("La saison est obligatoire");
        }
        if (count != null && count < 1) {
            throw new IllegalArgumentException("Le nombre de titres doit être >= 1");
        }
    }
}
