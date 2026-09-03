package com.wydad.digital.content.repository;

import com.wydad.digital.content.model.BackgroundImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BackgroundImageRepository extends JpaRepository<BackgroundImage, Long> {

    /** Vitrine publique : images actives d'une page, ordonnées. */
    List<BackgroundImage> findByPageKeyAndActiveTrueOrderByDisplayOrderAsc(String pageKey);

    /** Admin : toutes les images de toutes les pages, ordonnées par page puis displayOrder. */
    List<BackgroundImage> findAllByOrderByPageKeyAscDisplayOrderAsc();
}
