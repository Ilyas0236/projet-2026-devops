package com.wydad.digital.shop.controller;

import com.wydad.digital.shop.dto.OrderRequestDto;
import com.wydad.digital.shop.dto.OrderResponseDto;
import com.wydad.digital.shop.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/shop/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @PreAuthorize("hasRole('ADHERENT') or hasRole('ADMIN')")
    public ResponseEntity<OrderResponseDto> createOrder(
            @RequestHeader("X-User-Email") String userEmail,
            @Valid @RequestBody OrderRequestDto dto) {
        return ResponseEntity.ok(orderService.createOrder(userEmail, dto));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADHERENT') or hasRole('ADMIN')")
    public ResponseEntity<Page<OrderResponseDto>> getMyOrders(
            @RequestHeader("X-User-Email") String userEmail,
            Pageable pageable) {
        return ResponseEntity.ok(orderService.getUserOrders(userEmail, pageable));
    }

    @GetMapping("/{orderNumber}")
    @PreAuthorize("hasRole('ADHERENT') or hasRole('ADMIN')")
    public ResponseEntity<OrderResponseDto> getOrder(
            @RequestHeader("X-User-Email") String userEmail,
            @PathVariable String orderNumber) {
        return ResponseEntity.ok(orderService.getOrder(userEmail, orderNumber));
    }
}