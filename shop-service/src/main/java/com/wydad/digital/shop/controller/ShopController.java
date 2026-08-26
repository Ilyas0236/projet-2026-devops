package com.wydad.digital.shop.controller;

import com.wydad.digital.shop.dto.CartItemDto;
import com.wydad.digital.shop.dto.ProductDto;
import com.wydad.digital.shop.dto.ProductRequest;
import com.wydad.digital.shop.enums.SportSection;
import com.wydad.digital.shop.service.CartService;
import com.wydad.digital.shop.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/shop")
@RequiredArgsConstructor
public class ShopController {

    private final ProductService productService;
    private final CartService cartService;

    // ========== PRODUITS ==========
    @GetMapping("/products")
    public ResponseEntity<Page<ProductDto>> getProducts(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) SportSection sport,
            @RequestParam(required = false) String search,
            Pageable pageable) {

        Page<ProductDto> products;
        if (search != null && !search.isBlank()) {
            products = productService.searchProducts(search, pageable);
        } else if (categoryId != null) {
            products = productService.getProductsByCategory(categoryId, pageable);
        } else if (sport != null) {
            products = productService.getProductsBySport(sport, pageable);
        } else {
            products = productService.getAllActiveProducts(pageable);
        }
        return ResponseEntity.ok(products);
    }

    @GetMapping("/products/{id}")
    public ResponseEntity<ProductDto> getProduct(@PathVariable Long id) {
        return ResponseEntity.ok(productService.getProductById(id));
    }

    @PostMapping("/products")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductDto> createProduct(@RequestBody ProductRequest productRequest) {
        return ResponseEntity.ok(productService.createProduct(productRequest));
    }

    @PutMapping("/products/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductDto> updateProduct(@PathVariable Long id, @RequestBody ProductRequest productRequest) {
        return ResponseEntity.ok(productService.updateProduct(id, productRequest));
    }

    @DeleteMapping("/products/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }

    // ========== PANIER ==========
    @GetMapping("/cart")
    @PreAuthorize("hasAnyRole('ADHERENT','JOUEUR','STAFF','ENTRAINEUR','JOURNALISTE','PRESIDENT','PARENT','ADMIN')")
    public ResponseEntity<List<CartItemDto>> getCart(
            @RequestHeader("X-User-Email") String userEmail) {
        return ResponseEntity.ok(cartService.getCart(userEmail));
    }

    @PostMapping("/cart")
    @PreAuthorize("hasAnyRole('ADHERENT','JOUEUR','STAFF','ENTRAINEUR','JOURNALISTE','PRESIDENT','PARENT','ADMIN')")
    public ResponseEntity<CartItemDto> addToCart(
            @RequestHeader("X-User-Email") String userEmail,
            @Valid @RequestBody CartItemDto dto) {
        return ResponseEntity.ok(cartService.addToCart(userEmail, dto));
    }

    @PutMapping("/cart/{cartItemId}")
    @PreAuthorize("hasAnyRole('ADHERENT','JOUEUR','STAFF','ENTRAINEUR','JOURNALISTE','PRESIDENT','PARENT','ADMIN')")
    public ResponseEntity<Void> updateCartQuantity(
            @RequestHeader("X-User-Email") String userEmail,
            @PathVariable Long cartItemId,
            @RequestParam Integer quantity) {
        cartService.updateQuantity(userEmail, cartItemId, quantity);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/cart/{cartItemId}")
    @PreAuthorize("hasAnyRole('ADHERENT','JOUEUR','STAFF','ENTRAINEUR','JOURNALISTE','PRESIDENT','PARENT','ADMIN')")
    public ResponseEntity<Void> removeFromCart(
            @RequestHeader("X-User-Email") String userEmail,
            @PathVariable Long cartItemId) {
        cartService.removeFromCart(userEmail, cartItemId);
        return ResponseEntity.noContent().build();
    }
}