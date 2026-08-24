package com.wydad.digital.shop.controller;

import com.wydad.digital.shop.model.CartItem;
import com.wydad.digital.shop.model.Product;
import com.wydad.digital.shop.model.ProductVariant;
import com.wydad.digital.shop.repository.CartItemRepository;
import com.wydad.digital.shop.repository.ProductRepository;
import com.wydad.digital.shop.repository.ProductVariantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * B.12.b — Lancement d'une promotion SANS intervention base de données :
 * l'ADMIN crée le code via l'API, il s'applique réellement en commande
 * (remise plafonnée serveur), se désactive et s'épuise.
 *
 * Règle de sécurité : la gestion des codes promo est réservée ADMIN.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:shoppromo;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        // L'application.yml force le dialecte PostgreSQL : on le remplace pour H2.
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@AutoConfigureMockMvc
@Transactional
class PromoCodeControllerTest {

    private static final String BASE = "/api/shop/promo-codes";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductVariantRepository variantRepository;

    /** Aucun appel HTTP réel vers payment-service. */
    @MockBean
    private com.wydad.digital.shop.client.PaymentClient paymentClient;

    @MockBean
    private com.wydad.digital.shop.client.NotificationClient notificationClient;

    private Long variantA;
    private Long variantB;
    private Long productId;

    @BeforeEach
    void seed() {
        Product product = productRepository.save(Product.builder()
                .name("Echarpe WAC (test promo)")
                .basePrice(new BigDecimal("200.00"))
                .active(true)
                .build());
        this.productId = product.getId();
        // Deux variants : la contrainte d'unicité (email, variante) du panier
        // impose une ligne distincte par commande du même membre.
        this.variantA = variantRepository.save(ProductVariant.builder()
                .size(com.wydad.digital.shop.enums.ProductSize.UNIQUE)
                .stockQuantity(50)
                .product(product)
                .build()).getId();
        this.variantB = variantRepository.save(ProductVariant.builder()
                .size(com.wydad.digital.shop.enums.ProductSize.M)
                .stockQuantity(50)
                .product(product)
                .build()).getId();

        // Paiement E-cash simulé : toujours accepté.
        doAnswer(inv -> null).when(paymentClient).debitEcash(anyString(), any(BigDecimal.class), anyString());
    }

    /** Ajoute 1 écharpe au panier du membre sur le variant donné, renvoie l'id de ligne panier. */
    private long addToCart(String email, Long variantId, String variantLabel) {
        return cartItemRepository.save(CartItem.builder()
                .userEmail(email)
                .productVariantId(variantId)
                .productId(productId)
                .productName("Echarpe WAC (test promo)")
                .variantInfo(variantLabel)
                .quantity(1)
                .build()).getId();
    }

    /**
     * Corps de commande : la ligne panier est référencée par son id,
     * exactement comme le fait le frontend via OrderItemRequest.cartItemId.
     */
    private String orderBody(long cartItemId, String promo) {
        String base = "{\"shippingAddress\":\"12 rue du Stade\",\"shippingCity\":\"Casablanca\","
                + "\"shippingPhone\":\"0600000000\",\"clickAndCollect\":false,"
                + "\"items\":[{\"cartItemId\":" + cartItemId + "}]}";
        if (promo == null) {
            return base;
        }
        return base.replace("\"items\"", "\"promoCode\":\"" + promo + "\",\"items\"");
    }

    private String admin() {
        return "{\"code\":\"WAC20\",\"description\":\"Promotion rentrée\","
                + "\"discountPercent\":20,\"maxDiscountAmount\":50,\"minOrderAmount\":100,"
                + "\"maxUses\":1}";
    }

    @Test
    @DisplayName("Seul l'ADMIN gère les codes promo (ADHERENT et STAFF -> 403)")
    void seulAdminGererLesCodesPromo() throws Exception {
        mockMvc.perform(post(BASE)
                        .header("X-User-Id", "42")
                        .header("X-User-Email", "fan@wydad.ma")
                        .header("X-User-Role", "ADHERENT")
                        .contentType("application/json")
                        .content(admin()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get(BASE)
                        .header("X-User-Id", "42")
                        .header("X-User-Email", "fan@wydad.ma")
                        .header("X-User-Role", "ADHERENT"))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch(BASE + "/1/active")
                        .header("X-User-Id", "7")
                        .header("X-User-Email", "staff@wydad.ma")
                        .header("X-User-Role", "STAFF")
                        .contentType("application/json")
                        .content("{\"active\":false}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("L'ADMIN crée un code : il s'applique réellement en commande (remise plafonnée)")
    void codeCreeParAdminSappliqueEnCommande() throws Exception {
        mockMvc.perform(post(BASE)
                        .header("X-User-Id", "1")
                        .header("X-User-Email", "admin@wydad.ma")
                        .header("X-User-Role", "ADMIN")
                        .contentType("application/json")
                        .content(admin()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("WAC20"))
                .andExpect(jsonPath("$.currentUses").value(0))
                .andExpect(jsonPath("$.active").value(true));

        // Le membre met 200 DH dans son panier puis commande avec le code :
        // remise = min(20 % x 200 ; 50) = 40 DH.
        long cartItemId = addToCart("fan@wydad.ma", variantA, "UNIQUE");

        mockMvc.perform(post("/api/shop/orders")
                        .header("X-User-Email", "fan@wydad.ma")
                        .header("X-User-Id", "42")
                        .header("X-User-Role", "ADHERENT")
                        .contentType("application/json")
                        .content(orderBody(cartItemId, "WAC20")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.discountAmount").value(40.00))
                .andExpect(jsonPath("$.totalAmount").value(190.00)); // 200 - 40 + 30 livraison

        mockMvc.perform(get(BASE)
                        .header("X-User-Id", "1")
                        .header("X-User-Email", "admin@wydad.ma")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code == 'WAC20')].currentUses").value(1));
    }

    @Test
    @DisplayName("Code désactivé ou épuisé -> commande refusée")
    void codeDesactiveOuEpuiseRefuse() throws Exception {
        String body = mockMvc.perform(post(BASE)
                        .header("X-User-Id", "1")
                        .header("X-User-Email", "admin@wydad.ma")
                        .header("X-User-Role", "ADMIN")
                        .contentType("application/json")
                        .content(admin()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Number idNum = com.jayway.jsonpath.JsonPath.parse(body).read("$.id");
        long id = idNum.longValue();

        // maxUses = 1 : une première commande épuise le code.
        long first = addToCart("fan@wydad.ma", variantA, "UNIQUE");
        mockMvc.perform(post("/api/shop/orders")
                        .header("X-User-Email", "fan@wydad.ma")
                        .header("X-User-Id", "42")
                        .header("X-User-Role", "ADHERENT")
                        .contentType("application/json")
                        .content(orderBody(first, "WAC20")))
                .andExpect(status().isOk());

        // Deuxième commande : code épuisé -> 400, rien n'est débité ni persisté.
        long second = addToCart("fan@wydad.ma", variantB, "M");
        mockMvc.perform(post("/api/shop/orders")
                        .header("X-User-Email", "fan@wydad.ma")
                        .header("X-User-Id", "42")
                        .header("X-User-Role", "ADHERENT")
                        .contentType("application/json")
                        .content(orderBody(second, "WAC20")))
                .andExpect(status().isBadRequest());

        // L'ADMIN désactive le code : visible immédiatement dans la liste qu'il pilote.
        mockMvc.perform(patch(BASE + "/" + id + "/active")
                        .header("X-User-Id", "1")
                        .header("X-User-Email", "admin@wydad.ma")
                        .header("X-User-Role", "ADMIN")
                        .contentType("application/json")
                        .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }
}
