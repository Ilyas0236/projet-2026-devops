package com.wydad.digital.shop.service;

import com.wydad.digital.shop.dto.OrderRequestDto;
import com.wydad.digital.shop.dto.OrderResponseDto;
import com.wydad.digital.shop.enums.OrderStatus;
import com.wydad.digital.shop.enums.PaymentStatus;
import com.wydad.digital.shop.model.*;
import com.wydad.digital.shop.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class OrderService {

    private final ShopOrderRepository shopOrderRepository;
    private final com.wydad.digital.shop.client.NotificationClient notificationClient;
    private final CartItemRepository cartItemRepository;
    private final ProductVariantRepository productVariantRepository;
    private final PromoCodeRepository promoCodeRepository;
    private final StoreRepository storeRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;

    public OrderResponseDto createOrder(String userEmail, OrderRequestDto dto) {
        var cartItems = cartItemRepository.findByUserEmail(userEmail);
        if (cartItems.isEmpty()) {
            throw new RuntimeException("Panier vide");
        }

        // Verrouiller toutes les variantes du panier (tri par id pour éviter les deadlocks)
        // puis vérifier ET décrémenter le stock en un seul passage atomique.
        List<Long> variantIds = cartItems.stream()
                .map(CartItem::getProductVariantId)
                .distinct()
                .sorted()
                .toList();
        Map<Long, ProductVariant> lockedVariants = new HashMap<>();
        for (Long variantId : variantIds) {
            ProductVariant variant = productVariantRepository.findByIdForUpdate(variantId)
                    .orElseThrow(() -> new RuntimeException("Variante non trouvée"));
            lockedVariants.put(variantId, variant);
        }

        for (CartItem item : cartItems) {
            ProductVariant variant = lockedVariants.get(item.getProductVariantId());
            if (variant.getStockQuantity() < item.getQuantity()) {
                throw new RuntimeException("Stock insuffisant pour: " + item.getProductName());
            }
        }

        // Créer commande
        ShopOrder order = new ShopOrder();
        order.setOrderNumber(generateOrderNumber());
        order.setUserEmail(userEmail);
        order.setStatus(OrderStatus.PENDING);
        order.setPaymentStatus(PaymentStatus.PENDING);
        order.setShippingAddress(dto.getShippingAddress());
        order.setShippingCity(dto.getShippingCity());
        order.setShippingPhone(dto.getShippingPhone());
        order.setClickAndCollect(dto.getClickAndCollect() != null ? dto.getClickAndCollect() : false);

        // Click & Collect
        if (Boolean.TRUE.equals(order.getClickAndCollect()) && dto.getPickupStoreId() != null) {
            Store store = storeRepository.findById(dto.getPickupStoreId())
                    .orElseThrow(() -> new RuntimeException("Store non trouvé"));
            order.setPickupStore(store);
            order.setShippingCost(BigDecimal.ZERO);
        } else {
            order.setShippingCost(new BigDecimal("30.00"));
            order.setClickAndCollect(false);
        }

        // Ajouter items + calcul sous-total (variantes déjà verrouillées ci-dessus)
        BigDecimal subtotal = BigDecimal.ZERO;
        for (CartItem cartItem : cartItems) {
            ProductVariant variant = lockedVariants.get(cartItem.getProductVariantId());

            BigDecimal unitPrice = variant.getProduct().getBasePrice();
            if (cartItem.getCustomization() != null && cartItem.getCustomization().getExtraPrice() != null) {
                unitPrice = unitPrice.add(BigDecimal.valueOf(cartItem.getCustomization().getExtraPrice()));
            }

            BigDecimal itemTotal = unitPrice.multiply(BigDecimal.valueOf(cartItem.getQuantity()));
            subtotal = subtotal.add(itemTotal);

            // Décrémenter stock
            variant.setStockQuantity(variant.getStockQuantity() - cartItem.getQuantity());
            productVariantRepository.save(variant);

            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setProductId(cartItem.getProductId());
            orderItem.setProductName(cartItem.getProductName());
            orderItem.setProductImage(cartItem.getProductImage());
            orderItem.setVariantInfo(cartItem.getVariantInfo());
            orderItem.setUnitPrice(unitPrice);
            orderItem.setQuantity(cartItem.getQuantity());
            orderItem.setTotalPrice(itemTotal);
            orderItem.setCustomization(cartItem.getCustomization());

            order.getItems().add(orderItem);
        }

        order.setSubtotal(subtotal);

        // Promo code
        BigDecimal discount = BigDecimal.ZERO;
        if (dto.getPromoCode() != null && !dto.getPromoCode().isBlank()) {
            // Verrou pessimiste : l'incrément de currentUses ne peut pas dépasser maxUses
            PromoCode promo = promoCodeRepository.findActiveByCodeForUpdate(dto.getPromoCode())
                    .orElseThrow(() -> new RuntimeException("Code promo invalide"));

            LocalDateTime now = LocalDateTime.now();
            if (promo.getValidFrom() != null && now.isBefore(promo.getValidFrom())) {
                throw new RuntimeException("Code promo pas encore actif");
            }
            if (promo.getValidUntil() != null && now.isAfter(promo.getValidUntil())) {
                throw new RuntimeException("Code promo expiré");
            }
            if (promo.getMaxUses() != null && promo.getCurrentUses() >= promo.getMaxUses()) {
                throw new RuntimeException("Code promo épuisé");
            }
            if (subtotal.compareTo(promo.getMinOrderAmount() != null ? promo.getMinOrderAmount() : BigDecimal.ZERO) < 0) {
                throw new RuntimeException("Montant minimum non atteint pour ce code promo");
            }

            discount = subtotal.multiply(promo.getDiscountPercent().divide(new BigDecimal("100")));
            if (promo.getMaxDiscountAmount() != null && discount.compareTo(promo.getMaxDiscountAmount()) > 0) {
                discount = promo.getMaxDiscountAmount();
            }

            promo.setCurrentUses(promo.getCurrentUses() + 1);
            order.setPromoCodeUsed(promo.getCode());
        }
        order.setDiscountAmount(discount.setScale(2, RoundingMode.HALF_UP));

        // Total
        BigDecimal total = subtotal.add(order.getShippingCost()).subtract(order.getDiscountAmount());
        order.setTotalAmount(total.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP));

        // Historique statut
        OrderStatusHistory history = new OrderStatusHistory();
        history.setOrder(order);
        history.setStatus(OrderStatus.PENDING);
        history.setComment("Commande créée");
        order.getStatusHistory().add(history);

        ShopOrder saved = shopOrderRepository.save(order);
        cartItemRepository.deleteAllByUserEmail(userEmail);

        // Best-effort : une panne de notification ne doit pas annuler la commande
        notificationClient.notifyUser(
                null,
                userEmail,
                "Commande confirmée",
                "Votre commande " + saved.getOrderNumber() + " d'un montant de "
                        + saved.getTotalAmount() + " DH a été enregistrée.",
                "/profil/commandes");

        return mapToDto(saved);
    }

    @Transactional(readOnly = true)
    public OrderResponseDto getOrder(String userEmail, String orderNumber) {
        ShopOrder order = shopOrderRepository.findByOrderNumberAndUserEmail(orderNumber, userEmail)
                .orElseThrow(() -> new RuntimeException("Commande non trouvée"));
        return mapToDto(order);
    }

    @Transactional(readOnly = true)
    public Page<OrderResponseDto> getUserOrders(String userEmail, Pageable pageable) {
        return shopOrderRepository.findByUserEmailOrderByCreatedAtDesc(userEmail, pageable)
                .map(this::mapToDto);
    }

    @Transactional(readOnly = true)
    public Page<OrderResponseDto> getAllOrders(Pageable pageable) {
        return shopOrderRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(this::mapToDto);
    }

    private String generateOrderNumber() {
        return "WYD-" + java.time.Year.now().getValue() + "-" +
                UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    private OrderResponseDto mapToDto(ShopOrder o) {
        return OrderResponseDto.builder()
                .orderNumber(o.getOrderNumber())
                .status(o.getStatus().name())
                .paymentStatus(o.getPaymentStatus().name())
                .subtotal(o.getSubtotal())
                .shippingCost(o.getShippingCost())
                .discountAmount(o.getDiscountAmount())
                .totalAmount(o.getTotalAmount())
                .trackingNumber(o.getTrackingNumber())
                .createdAt(o.getCreatedAt())
                .items(o.getItems().stream()
                        .map(i -> OrderResponseDto.OrderItemDto.builder()
                                .productName(i.getProductName())
                                .productImage(i.getProductImage())
                                .variantInfo(i.getVariantInfo())
                                .quantity(i.getQuantity())
                                .unitPrice(i.getUnitPrice())
                                .totalPrice(i.getTotalPrice())
                                .build())
                        .toList())
                .build();
    }
}