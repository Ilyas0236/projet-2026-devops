package com.wydad.digital.election.controller;

import com.wydad.digital.election.dto.PollDtos.CreatePollRequest;
import com.wydad.digital.election.dto.PollDtos.PollResponse;
import com.wydad.digital.election.filter.UserContext;
import com.wydad.digital.election.service.PollService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * B.2 — Sondages : création/clôture réservées ADMIN, vote ouvert aux
 * membres authentifiés (ADHERENT minimum). L'identité du votant vient
 * TOUJOURS du contexte JWT transmis par la passerelle.
 *
 * Migration depuis sports-service (audit thématique) : un sondage est de la
 * GOUVERNANCE/PARTICIPATION, même famille que l'élection présidentielle.
 */
@RestController
@RequestMapping("/api/polls")
@RequiredArgsConstructor
public class PollController {

    private final PollService pollService;

    @GetMapping("/active")
    public ResponseEntity<List<PollResponse>> getActivePolls() {
        return ResponseEntity.ok(pollService.getActivePolls());
    }

    @PostMapping("/{id}/vote")
    @PreAuthorize("hasAnyRole('VISITEUR','ADHERENT','PARENT','JOUEUR','STAFF','ENTRAINEUR','JOURNALISTE','PRESIDENT','ADMIN')")
    public ResponseEntity<PollResponse> vote(@PathVariable Long id, @RequestParam int optionIndex) {
        return ResponseEntity.ok(pollService.vote(id, optionIndex));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PollResponse> createPoll(@RequestBody CreatePollRequest request) {
        String email = UserContext.getCurrentUserEmail();
        return ResponseEntity.status(HttpStatus.CREATED).body(pollService.createPoll(request, email));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PollResponse> closePoll(@PathVariable Long id) {
        return ResponseEntity.ok(pollService.closePoll(id));
    }
}
