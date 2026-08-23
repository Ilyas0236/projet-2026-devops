package com.wydad.digital.shop.service;

import com.wydad.digital.shop.client.NotificationClient;
import com.wydad.digital.shop.client.PaymentClient;
import com.wydad.digital.shop.dto.OrderRequestDto;
import com.wydad.digital.shop.dto.OrderResponseDto;
import com.wydad.digital.shop.model.CartItem;
import com.wydad.digital.shop.model.Category;
import com.wydad.digital.shop.model.Product;
import com.wydad.digital.shop.model.ProductVariant;
import com.wydad.digital.shop.repository.CartItemRepository;
import com.wydad.digital.shop.repository.CategoryRepository;
import com.wydad.digital.shop.repository.ProductRepository;
import com.wydad.digital.shop.repository.ProductVariantRepository;
import com.wydad.digital.shop.repository.ShopOrderRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * B.1 — Tests metier de la commande boutique :
 * 1. le prix de la commande est recalcule depuis la base (prix du produit
 *    + extra de personnalisation), jamais depuis une valeur client ;
 * 2. si le debit E-cash echoue, la commande est refusee et la transaction
 *    annulee : ni stock decremente, ni code promo consomme, ni panier vide.
 *
 * H2 en mode compatibilite PostgreSQL (supporte SELECT ... FOR UPDATE,
 * mecanisme des verrous pessimistes) ; a revalider aussi contre le
 * PostgreSQL reel (docker-compose).
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:shoptest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        // L'application.yml force le dialecte PostgreSQL : on le remplace pour H2.
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrderServiceTest {

    private static final String EMAIL = "fan@wydad.ma";

    @Autowired
    private OrderService orderService;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private ProductVariantRepository variantRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ShopOrderRepository orderRepository;

    /** Aucun appel HTTP reel vers payment-service / notification-service. */
    @MockBean
    private PaymentClient paymentClient;

    @MockBean
    private NotificationClient notificationClient;

    private Long variantId;

    @BeforeAll
    void seedCatalogAndCart() {
        Product product = productRepository.save(Product.builder()
                .name("Maillot Domicile WAC (test)")
                .basePrice(new BigDecimal("299.00"))
                .active(true)
                .build());

        ProductVariant variant = variantRepository.save(ProductVariant.builder()
                .size(com.wydad.digital.shop.enums.ProductSize.M)
                .color("Rouge")
                .stockQuantity(10)
                .product(product)
                .build());

        this.variantId = variant.getId();

        cartItemRepository.save(CartItem.builder()
                .userEmail(EMAIL)
                .productVariantId(variantId)
                .productId(product.getId())
                .productName(product.getName())
                .variantInfo("M / Rouge")
                .quantity(2)
                .build());
    }

    @AfterEach
    void resetMocks() {
        org.mockito.Mockito.reset(paymentClient);
        // debitEcash est void : stub par defaut = ne rien faire (paiement OK).
        org.mockito.Mockito.doAnswer(inv -> null)
                .when(paymentClient).debitEcash(anyString(), any(), anyString());
    }

    private OrderRequestDto request(String promoCode) {
        return OrderRequestDto.builder()
                .shippingAddress("12 rue du Stade")
                .shippingCity("Casablanca")
                .shippingPhone("0600000000")
                .promoCode(promoCode)
                .clickAndCollect(false)
                .items(List.of())
                .build();
    }

    /**
     * Le client ne fournit aucun champ prix dans OrderRequestDto : le total
     * est recalcule serveur depuis la base (2 x 299 DH + 30 DH livraison).
     */
    @Test
    void prixDelaCommandeEstRecalculeDepuisLaBase() {
        int stockAvant = variantRepository.findById(variantId).orElseThrow().getStockQuantity();

        OrderResponseDto order = orderService.createOrder(EMAIL, request(null));

        assertEquals(0, new BigDecimal("628.00").compareTo(order.getTotalAmount()),
                "Total = 2 x 299 DH (prix base) + 30 DH livraison, jamais une valeur client");
        verify(paymentClient).debitEcash(eq(EMAIL),
                eq(new BigDecimal("628.00")), anyString());

        assertEquals("PAYMENT_RECEIVED", order.getStatus());
        assertEquals(stockAvant - 2,
                variantRepository.findById(variantId).orElseThrow().getStockQuantity());
        assertTrue(orderRepository.findByOrderNumberAndUserEmail(
                        order.getOrderNumber(), EMAIL).isPresent(),
                "Commande persistee");
        assertTrue(cartItemRepository.findByUserEmail(EMAIL).isEmpty(),
                "Panier vide apres commande confirmee");
    }

    /**
     * Si payment-service refuse le debit, createOrder doit echouer et la
     * transaction locale etre annulee : stock intact, panier intact.
     * (Le premier test a vide le panier : contexte PER_CLASS partage, on
     * re-seed un article ici.)
     */
    @Test
    void commandeRefuseeSiLeDebitWalletEchoue() {
        Product product = productRepository.findAll().get(0);
        cartItemRepository.save(CartItem.builder()
                .userEmail(EMAIL)
                .productVariantId(variantId)
                .productId(product.getId())
                .productName(product.getName())
                .variantInfo("M / Rouge")
                .quantity(1)
                .build());

        doThrow(new RuntimeException("Paiement refuse : solde E-cash insuffisant"))
                .when(paymentClient).debitEcash(anyString(), any(), anyString());

        int stockAvant = variantRepository.findById(variantId).orElseThrow().getStockQuantity();
        int itemsPanierAvant = cartItemRepository.findByUserEmail(EMAIL).size();

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> orderService.createOrder(EMAIL, request(null)));

        assertTrue(ex.getMessage().contains("solde E-cash insuffisant"),
                "L'erreur de paiement remonte au client");

        assertEquals(stockAvant, variantRepository.findById(variantId).orElseThrow().getStockQuantity(),
                "Rollback : le stock n'est PAS decremente quand le paiement echoue");
        assertEquals(itemsPanierAvant, cartItemRepository.findByUserEmail(EMAIL).size(),
                "Rollback : le panier n'est PAS vide quand le paiement echoue");
        verify(paymentClient).debitEcash(eq(EMAIL), any(), anyString());
    }
}
