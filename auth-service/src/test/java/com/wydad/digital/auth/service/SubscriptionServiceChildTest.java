package com.wydad.digital.auth.service;

import com.wydad.digital.auth.client.PaymentClient;
import com.wydad.digital.auth.client.SportsClient;
import com.wydad.digital.auth.dto.subscription.PurchaseSubscriptionRequest;
import com.wydad.digital.auth.dto.subscription.SubscriptionResponse;
import com.wydad.digital.auth.model.Role;
import com.wydad.digital.auth.model.StatutCompte;
import com.wydad.digital.auth.model.User;
import com.wydad.digital.auth.model.subscription.SubscriptionPlan;
import com.wydad.digital.auth.model.subscription.SubscriptionZoneCode;
import com.wydad.digital.auth.model.subscription.UserSubscription;
import com.wydad.digital.auth.model.subscription.UserSubscriptionStatus;
import com.wydad.digital.auth.repository.UserRepository;
import com.wydad.digital.auth.repository.subscription.SubscriptionPlanRepository;
import com.wydad.digital.auth.repository.subscription.UserSubscriptionRepository;
import com.wydad.digital.auth.service.subscription.SubscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * B.18 — Tests unitaires du flow « PARENT achète un abonnement pour
 * son fils académie ». On mocke les dépendances externes (PaymentClient,
 * SportsClient, PdfService, QrCodeService) pour isoler la logique métier.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:subchildtest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class SubscriptionServiceChildTest {

    @Autowired private SubscriptionService subscriptionService;
    @Autowired private UserRepository userRepository;
    @Autowired private SubscriptionPlanRepository planRepository;
    @Autowired private UserSubscriptionRepository subscriptionRepository;

    @MockBean private PaymentClient paymentClient;
    @MockBean private SportsClient sportsClient;
    @MockBean private PdfService pdfService;
    @MockBean private QrCodeService qrCodeService;

    private static final Long PARENT_ID = 100L;
    private static final Long ACADEMY_ID = 42L;
    private static final String PARENT_EMAIL = "parent.test@wydad.ma";
    private static final String CHILD_NAME = "Younes Test";

    @BeforeEach
    void seed() throws Exception {
        subscriptionRepository.deleteAll();
        userRepository.deleteAll();
        planRepository.deleteAll();

        User parent = User.builder()
                .email(PARENT_EMAIL)
                .phone("0600000001")
                .password("x")
                .firstName("Parent").lastName("Test")
                .role(Role.PARENT)
                .statutCompte(StatutCompte.VALIDE)
                .active(true)
                .build();
        // Forcer l'id pour le test
        parent = userRepository.save(parent);
        // Note : l'id auto-incrémenté ne sera pas PARENT_ID, on le récupère après.
        Long actualParentId = parent.getId();

        SubscriptionPlan plan = SubscriptionPlan.builder()
                .code("PEL-6")  // matche SubscriptionZoneCode.PELOUSE_ZONE_6 → zoneCode non null
                .name("Plan Test")
                .regularPrice(new BigDecimal("200"))
                .adherentPrice(new BigDecimal("200"))
                .isActive(true)
                .build();
        planRepository.save(plan);

        when(paymentClient.debitEcash(anyString(), any(BigDecimal.class), anyString()))
                .thenReturn("TX-REF-TEST");
        when(pdfService.generateSubscriptionPdf(any(), any(), anyString()))
                .thenReturn("/tmp/test-pdf");
        // QrCodeService.generateQrCode(...) throws Exception → on ne peut pas
        // utiliser when(...).thenReturn() qui compilerait, on passe par
        // doReturn().when(...) qui ne déclenche pas l'appel réel au setup.
        org.mockito.Mockito.doReturn("base64-fake")
                .when(qrCodeService)
                .generateQrCode(anyString(), any(Integer.class), any(Integer.class));
    }

    @Test
    @DisplayName("B.18 — achat pour le fils : crée un User shadow, l'abonnement est rattaché au fils")
    void purchaseForChild_createsShadowUserAndAssignsSubscription() {
        // 1) Sauvegarder le parent et récupérer son id réel
        User parent = userRepository.findByEmail(PARENT_EMAIL).orElseThrow();
        Long actualParentId = parent.getId();

        // 2) Mock du lookup sports : {id, parentUserId, childFullName}
        when(sportsClient.getAcademyMember(eq(ACADEMY_ID)))
                .thenReturn(Map.of("id", ACADEMY_ID, "parentUserId", actualParentId, "childFullName", CHILD_NAME));

        // 3) Appel du service avec beneficiaryAcademyMemberId
        PurchaseSubscriptionRequest req = new PurchaseSubscriptionRequest(
                "PEL-6", "4242424242424242", "12/29", "123", "000000", ACADEMY_ID);
        SubscriptionResponse resp = subscriptionService.purchase(PARENT_EMAIL, req);

        // 4) Vérifications
        assertNotNull(resp.id());
        assertNotNull(resp.beneficiaryAcademyMemberId());
        assertEquals(ACADEMY_ID, resp.beneficiaryAcademyMemberId());
        assertEquals(PARENT_EMAIL, resp.parentPayerEmail());
        // L'abonnement doit être rattaché à l'User shadow du fils
        assertEquals("enfant-" + ACADEMY_ID + "@wac.parent", resp.email());

        // 5) L'User shadow doit exister en base
        User shadow = userRepository.findByEmail("enfant-" + ACADEMY_ID + "@wac.parent")
                .orElseThrow(() -> new AssertionError("User shadow non créé"));
        assertEquals(Role.ADHERENT, shadow.getRole());
        assertEquals(StatutCompte.VALIDE, shadow.getStatutCompte());
    }

    @Test
    @DisplayName("B.18 — IDOR : un parent qui tente d'acheter pour l'enfant d'un autre parent est refusé")
    void purchaseForChild_notYourChild_throws() {
        User parent = userRepository.findByEmail(PARENT_EMAIL).orElseThrow();
        // Le mock sports dit que l'enfant est rattaché à un AUTRE parent (id 999)
        when(sportsClient.getAcademyMember(eq(ACADEMY_ID)))
                .thenReturn(Map.of("id", ACADEMY_ID, "parentUserId", 999L, "childFullName", "Autre Enfant"));

        PurchaseSubscriptionRequest req = new PurchaseSubscriptionRequest(
                "PEL-6", "4242424242424242", "12/29", "123", "000000", ACADEMY_ID);
        SubscriptionService.NotYourChildException ex = assertThrows(
                SubscriptionService.NotYourChildException.class,
                () -> subscriptionService.purchase(PARENT_EMAIL, req));
        assertNotNull(ex.getMessage());
    }

    @Test
    @DisplayName("B.18 — achat pour soi (sans beneficiary) : comportement inchangé")
    void purchaseForSelf_unchangedBehavior() {
        PurchaseSubscriptionRequest req = new PurchaseSubscriptionRequest(
                "PEL-6", "4242424242424242", "12/29", "123", "000000", null);
        SubscriptionResponse resp = subscriptionService.purchase(PARENT_EMAIL, req);

        assertNotNull(resp.id());
        // Pas de traçabilité enfant pour un achat "pour soi"
        assertEquals(null, resp.beneficiaryAcademyMemberId());
        assertEquals(null, resp.parentPayerEmail());
        assertEquals(PARENT_EMAIL, resp.email());
    }
}
