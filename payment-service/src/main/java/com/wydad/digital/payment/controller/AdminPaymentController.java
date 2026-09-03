package com.wydad.digital.payment.controller;

import com.wydad.digital.payment.client.UserClient;
import com.wydad.digital.payment.dto.DonationResponseDto;
import com.wydad.digital.payment.model.Transaction;
import com.wydad.digital.payment.model.TransactionType;
import com.wydad.digital.payment.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;

/**
 * B.12 / inventaire admin — Endpoints ADMIN pour lister les transactions
 * (notamment les dons) avec filtres date + email. Renvoie du
 * {@link DonationResponseDto} enrichi avec le fullName (batch lookup
 * vers auth-service pour éviter le N+1).
 */
@RestController
@RequestMapping("/api/payment/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminPaymentController {

    private final TransactionRepository transactionRepository;
    private final UserClient userClient;

    /**
     * Filtre les dons (type=DON) avec : startDate, endDate (ISO date),
     * userEmail (filtre exact), page, size.
     */
    @GetMapping("/donations/filter")
    public Page<DonationResponseDto> filterDonations(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDateTime endDate,
            @RequestParam(required = false) String userEmail,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        // Étend la borne endDate à la fin de journée (23:59:59.999) pour
        // inclure toutes les transactions du jour.
        LocalDateTime endOfDay = endDate != null ? endDate.with(LocalTime.MAX) : null;
        Pageable pageable = PageRequest.of(page, size);
        Page<Transaction> txPage = transactionRepository.adminFilterByTypeAndDates(
                TransactionType.DON, startDate, endOfDay, userEmail, pageable);
        // 1 batch lookup vers auth-service pour les fullNames
        Map<String, String> fullNames = userClient.getAllFullNamesByEmail();
        return txPage.map(t -> new DonationResponseDto(
                t.getId(),
                t.getEmail(),
                fullNames.getOrDefault(t.getEmail(), t.getEmail()),
                t.getAmount(),
                t.getDescription(),
                t.getReference(),
                "SUCCESS",  // les dons enregistrés sont systématiquement validés
                t.getCreatedAt()
        ));
    }
}
