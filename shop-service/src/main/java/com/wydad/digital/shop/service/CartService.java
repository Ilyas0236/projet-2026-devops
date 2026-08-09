package com.wydad.digital.shop.service;

import com.wydad.digital.shop.dto.CartItemDto;
import com.wydad.digital.shop.model.CartItem;
import com.wydad.digital.shop.model.JerseyCustomization;
import com.wydad.digital.shop.model.ProductVariant;
import com.wydad.digital.shop.repository.CartItemRepository;
import com.wydad.digital.shop.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class CartService {

    private final CartItemRepository cartItemRepository;
    private final ProductVariantRepository productVariantRepository;

    public CartItemDto addToCart(String userEmail, CartItemDto dto) {
        ProductVariant variant = productVariantRepository.findById(dto.getProductVariantId())
                .orElseThrow(() -> new RuntimeException("Variante non trouvée"));

        if (variant.getStockQuantity() < dto.getQuantity()) {
            throw new RuntimeException("Stock insuffisant. Disponible: " + variant.getStockQuantity());
        }

        CartItem item = cartItemRepository
                .findByUserEmailAndProductVariantId(userEmail, dto.getProductVariantId())
                .orElse(CartItem.builder()
                        .userEmail(userEmail)
                        .build());

        item.setProductVariantId(variant.getId());
        item.setProductId(variant.getProduct().getId());
        item.setProductName(variant.getProduct().getName());
        item.setProductImage(variant.getProduct().getImages().isEmpty() ? null :
                variant.getProduct().getImages().get(0).getUrl());
        item.setVariantInfo((variant.getSize() != null ? variant.getSize().name() : "") + " - " + variant.getColor());
        item.setQuantity(item.getId() != null ? item.getQuantity() + dto.getQuantity() : dto.getQuantity());

        if (dto.getCustomization() != null && Boolean.TRUE.equals(variant.getProduct().getCustomizable())) {
            JerseyCustomization jc = JerseyCustomization.builder()
                    .playerName(dto.getCustomization().getPlayerName())
                    .playerNumber(dto.getCustomization().getPlayerNumber())
                    .fontFamily(dto.getCustomization().getFontFamily())
                    .fontColor(dto.getCustomization().getFontColor())
                    .patchType(dto.getCustomization().getPatchType())
                    .extraPrice(50.0)
                    .build();
            item.setCustomization(jc);
        }

        return mapToDto(cartItemRepository.save(item));
    }

    public List<CartItemDto> getCart(String userEmail) {
        return cartItemRepository.findByUserEmail(userEmail).stream()
                .map(this::mapToDto)
                .toList();
    }

    public void updateQuantity(String userEmail, Long cartItemId, Integer quantity) {
        CartItem item = cartItemRepository.findById(cartItemId)
                .filter(c -> c.getUserEmail().equals(userEmail))
                .orElseThrow(() -> new RuntimeException("Article non trouvé dans le panier"));

        ProductVariant variant = productVariantRepository.findById(item.getProductVariantId())
                .orElseThrow(() -> new RuntimeException("Variante non trouvée"));

        if (variant.getStockQuantity() < quantity) {
            throw new RuntimeException("Stock insuffisant");
        }

        item.setQuantity(quantity);
        cartItemRepository.save(item);
    }

    public void removeFromCart(String userEmail, Long cartItemId) {
        cartItemRepository.deleteByUserEmailAndId(userEmail, cartItemId);
    }

    public void clearCart(String userEmail) {
        cartItemRepository.deleteAllByUserEmail(userEmail);
    }

    private CartItemDto mapToDto(CartItem item) {
        return CartItemDto.builder()
                .id(item.getId())
                .productVariantId(item.getProductVariantId())
                .productId(item.getProductId())
                .productName(item.getProductName())
                .productImage(item.getProductImage())
                .variantInfo(item.getVariantInfo())
                .quantity(item.getQuantity())
                .build();
    }
}