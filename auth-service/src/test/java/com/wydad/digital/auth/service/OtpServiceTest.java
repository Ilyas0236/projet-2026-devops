package com.wydad.digital.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OtpServiceTest {

    private OtpService otpService;

    @BeforeEach
    void setUp() throws Exception {
        otpService = new OtpService();
        ReflectionTestUtils.setField(otpService, "mockDeliveryEnabled", false);
    }

    private Map<String, ?> store() throws Exception {
        Field f = OtpService.class.getDeclaredField("otpStore");
        f.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, ?> map = (Map<String, ?>) f.get(otpService);
        return map;
    }

    @Test
    @DisplayName("generateOtp produit un code à 6 chiffres")
    void generateOtp_isSixDigits() {
        String code = otpService.generateOtp("user@test.ma");
        assertTrue(code.matches("\\d{6}"));
    }

    @Test
    @DisplayName("verifyOtp accepte le bon code puis invalide l'OTP (usage unique)")
    void verifyOtp_validCode_consumedOnce() {
        String code = otpService.generateOtp("user@test.ma");
        assertTrue(otpService.verifyOtp("user@test.ma", code));
        // usage unique : rejeté après consommation
        assertFalse(otpService.verifyOtp("user@test.ma", code));
    }

    @Test
    @DisplayName("verifyOtp rejette un code erroné sans consommer l'OTP avant 5 échecs")
    void verifyOtp_wrongCode_notConsumedBeforeMaxAttempts() {
        String code = otpService.generateOtp("user@test.ma");
        assertFalse(otpService.verifyOtp("user@test.ma", "000001"));
        assertFalse(otpService.verifyOtp("user@test.ma", "000002"));
        assertFalse(otpService.verifyOtp("user@test.ma", "000003"));
        assertFalse(otpService.verifyOtp("user@test.ma", "000004"));
        // Le code correct reste utilisable tant que le max de tentatives n'est pas atteint
        assertTrue(otpService.verifyOtp("user@test.ma", code));
    }

    @Test
    @DisplayName("après 5 tentatives échouées, même le bon code est refusé (anti force brute)")
    void verifyOtp_invalidatedAfterFiveFailedAttempts() {
        String code = otpService.generateOtp("user@test.ma");
        for (int i = 1; i <= 5; i++) {
            assertFalse(otpService.verifyOtp("user@test.ma", String.format("%06d", i)));
        }
        // 5 échecs -> OTP invalidé, même avec le bon code
        assertFalse(otpService.verifyOtp("user@test.ma", code));
    }

    @Test
    @DisplayName("le code n'est jamais stocké en clair dans la mémoire interne")
    void storedValue_isNotPlainCode() throws Exception {
        String code = otpService.generateOtp("user@test.ma");
        Object entry = store().get("user@test.ma");
        assertNotNull(entry);
        String stored = entry.toString();
        assertFalse(stored.contains(code), "Le code brut ne doit pas apparaître en mémoire : " + stored);
    }

    @Test
    @DisplayName("peekMockCode renvoie null quand mock-delivery est désactivé")
    void peekMockCode_disabled_returnsNull() {
        otpService.generateOtp("user@test.ma");
        assertNull(otpService.peekMockCode("user@test.ma"));
    }

    @Test
    @DisplayName("peekMockCode renvoie le code uniquement si mock-delivery est activé (avant génération)")
    void peekMockCode_enabled_returnsCode() {
        ReflectionTestUtils.setField(otpService, "mockDeliveryEnabled", true);
        String code = otpService.generateOtp("user@test.ma");
        assertEquals(code, otpService.peekMockCode("user@test.ma"));
        assertNull(otpService.peekMockCode("inconnu@test.ma"));
    }
}
