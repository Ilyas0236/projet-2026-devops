package com.wydad.digital.content.service;

import com.wydad.digital.content.model.ClubLegend;
import com.wydad.digital.content.repository.ClubLegendRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Hall of Fame du club : écriture ADMIN uniquement (rôle vérifié au
 * contrôleur via @PreAuthorize), lecture publique. Validation métier
 * (champs obligatoires, cohérence des années) testée par ClubLegendSecurityTest.
 */
@Service
@RequiredArgsConstructor
public class ClubLegendService {

    private final ClubLegendRepository legendRepository;

    @Transactional(readOnly = true)
    public List<ClubLegend> getPublicLegends() {
        return legendRepository.findByActiveTrueOrderByDisplayOrderAsc();
    }

    @Transactional(readOnly = true)
    public List<ClubLegend> getAllLegends() {
        return legendRepository.findAllByOrderByDisplayOrderAsc();
    }

    @Transactional
    public ClubLegend create(String name, String nickname, String role, Integer yearFrom,
                             Integer yearTo, String biography, String imageUrl, Integer displayOrder) {
        validate(name, role, yearFrom, yearTo);
        return legendRepository.save(ClubLegend.builder()
                .name(name.trim())
                .nickname(nickname != null && !nickname.isBlank() ? nickname.trim() : null)
                .role(role.trim())
                .yearFrom(yearFrom)
                .yearTo(yearTo)
                .biography(biography != null && !biography.isBlank() ? biography.trim() : null)
                .imageUrl(imageUrl != null && !imageUrl.isBlank() ? imageUrl.trim() : null)
                .displayOrder(displayOrder != null ? displayOrder : 0)
                .build());
    }

    @Transactional
    public ClubLegend update(Long id, String name, String nickname, String role, Integer yearFrom,
                             Integer yearTo, String biography, String imageUrl, Integer displayOrder,
                             Boolean active) {
        ClubLegend l = legendRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Légende non trouvée: " + id));
        if (name != null && !name.isBlank()) l.setName(name.trim());
        if (nickname != null) l.setNickname(nickname.isBlank() ? null : nickname.trim());
        if (role != null && !role.isBlank()) l.setRole(role.trim());
        if (yearFrom != null) l.setYearFrom(yearFrom);
        if (yearTo != null) l.setYearTo(yearTo);
        validateYears(l.getYearFrom(), l.getYearTo());
        if (biography != null) l.setBiography(biography.isBlank() ? null : biography.trim());
        if (imageUrl != null) l.setImageUrl(imageUrl.isBlank() ? null : imageUrl.trim());
        if (displayOrder != null) l.setDisplayOrder(displayOrder);
        if (active != null) l.setActive(active);
        return legendRepository.save(l);
    }

    @Transactional
    public void delete(Long id) {
        if (!legendRepository.existsById(id)) {
            throw new EntityNotFoundException("Légende non trouvée: " + id);
        }
        legendRepository.deleteById(id);
    }

    private void validate(String name, String role, Integer yearFrom, Integer yearTo) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Le nom de la légende est obligatoire");
        }
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("Le poste/discipline est obligatoire");
        }
        if (yearFrom == null) {
            throw new IllegalArgumentException("La première année est obligatoire");
        }
        validateYears(yearFrom, yearTo);
    }

    /** Analyse aux limites ISTQB : année dans [1900, année courante], yearTo >= yearFrom. */
    private void validateYears(Integer yearFrom, Integer yearTo) {
        int currentYear = java.time.Year.now().getValue();
        if (yearFrom < 1900 || yearFrom > currentYear) {
            throw new IllegalArgumentException(
                    "La première année doit être entre 1900 et " + currentYear);
        }
        if (yearTo != null && yearTo < yearFrom) {
            throw new IllegalArgumentException(
                    "La dernière année ne peut pas précéder la première");
        }
    }
}
