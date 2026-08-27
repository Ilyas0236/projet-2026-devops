package com.wydad.digital.ticket.controller;

import com.wydad.digital.ticket.dto.SectionPatchRequest;
import com.wydad.digital.ticket.dto.SectionResponse;
import com.wydad.digital.ticket.service.EventService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de gestion fine d'une section de billetterie (ADMIN).
 *
 * <p>Séparés de {@link EventController} car ils gèrent l'unité "section" et non
 * l'unité "événement". L'objectif principal est de permettre à l'admin de
 * corriger le prix d'une section SANS casser l'historique des billets vendus
 * (impossible via un PUT sur l'événement, qui supprimerait puis recréerait
 * les sections, violant la FK {@code tickets.section_id}).</p>
 */
@RestController
@RequestMapping("/api/ticket/sections")
@RequiredArgsConstructor
public class SectionController {

    private final EventService eventService;

    /**
     * PATCH /api/ticket/sections/{id} — modifie partiellement une section.
     * Body : {@link SectionPatchRequest} (tous champs optionnels).
     */
    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SectionResponse> updateSection(
            @PathVariable Long id,
            @RequestBody SectionPatchRequest request) {
        return ResponseEntity.ok(eventService.updateSection(id, request));
    }
}
