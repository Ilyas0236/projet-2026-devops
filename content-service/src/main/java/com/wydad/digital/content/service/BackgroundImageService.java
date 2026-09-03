package com.wydad.digital.content.service;

import com.wydad.digital.content.model.BackgroundImage;
import com.wydad.digital.content.repository.BackgroundImageRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Gestion des images de fond par page (admin-parametres, onglet « Backgrounds »).
 * Écriture ADMIN uniquement, lecture publique par {@code pageKey}.
 */
@Service
@RequiredArgsConstructor
public class BackgroundImageService {

    private final BackgroundImageRepository repository;

    @Transactional(readOnly = true)
    public List<BackgroundImage> getPublicForPage(String pageKey) {
        if (pageKey == null || pageKey.isBlank()) return List.of();
        return repository.findByPageKeyAndActiveTrueOrderByDisplayOrderAsc(pageKey.trim().toUpperCase());
    }

    @Transactional(readOnly = true)
    public List<BackgroundImage> getAll() {
        return repository.findAllByOrderByPageKeyAscDisplayOrderAsc();
    }

    @Transactional
    public BackgroundImage create(String pageKey, String imageUrl, String caption, Integer displayOrder) {
        if (pageKey == null || pageKey.isBlank()) {
            throw new IllegalArgumentException("La page est obligatoire");
        }
        if (imageUrl == null || imageUrl.isBlank()) {
            throw new IllegalArgumentException("L'URL de l'image est obligatoire");
        }
        return repository.save(BackgroundImage.builder()
                .pageKey(pageKey.trim().toUpperCase())
                .imageUrl(imageUrl.trim())
                .caption(caption != null && !caption.isBlank() ? caption.trim() : null)
                .displayOrder(displayOrder != null ? displayOrder : 0)
                .active(true)
                .build());
    }

    @Transactional
    public BackgroundImage update(Long id, String caption, Integer displayOrder, Boolean active) {
        BackgroundImage bg = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Background non trouvé: " + id));
        if (caption != null) bg.setCaption(caption.isBlank() ? null : caption.trim());
        if (displayOrder != null) bg.setDisplayOrder(displayOrder);
        if (active != null) bg.setActive(active);
        return repository.save(bg);
    }

    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new EntityNotFoundException("Background non trouvé: " + id);
        }
        repository.deleteById(id);
    }
}
