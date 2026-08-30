package com.wydad.digital.ticket.controller;

import com.wydad.digital.ticket.dto.TicketCategoryResponse;
import com.wydad.digital.ticket.enums.TicketCategory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/**
 * Grille tarifaire publique (lue par la home et le formulaire admin).
 *
 * <p>Endpoint non authentifié : la grille tarifaire est une donnée
 * publique (les visiteurs voient les prix avant d'acheter, comme
 * demandé par le module B.28).</p>
 */
@RestController
@RequestMapping("/api/ticket/categories")
public class CategoryController {

    @GetMapping
    public ResponseEntity<List<TicketCategoryResponse>> list() {
        List<TicketCategoryResponse> out = Arrays.stream(TicketCategory.values())
                .map(TicketCategoryResponse::from)
                .toList();
        return ResponseEntity.ok(out);
    }
}
