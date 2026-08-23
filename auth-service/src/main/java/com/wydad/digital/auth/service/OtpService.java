package com.wydad.digital.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class OtpService {

    private static final int MAX_ATTEMPTS = 5;
    private static final int TTL_MINUTES = 10;

    // Stockage temporaire en mémoire : email -> (hash du code, expiration, tentatives)
    private final Map<String, OtpData> otpStore = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    @Value("${app.otp.mock-delivery:false}")
    private boolean mockDeliveryEnabled;

    public String generateOtp(String email) {
        String code = String.format("%06d", random.nextInt(1_000_000));
        // Le code brut n'est conservé en mémoire QUE si le canal de démonstration
        // est activé ; sinon, seul le hash SHA-256 est stocké.
        String plain = mockDeliveryEnabled ? code : null;
        otpStore.put(email, new OtpData(hashBytes(code), plain,
                LocalDateTime.now().plusMinutes(TTL_MINUTES), new AtomicInteger(0)));
        return code;
    }

    /**
     * Vérifie le code OTP soumis. Le hash est comparé en temps constant ; après
     * {@value #MAX_ATTEMPTS} tentatives échouées, l'OTP est invalidé
     * (anti force brute).
     */
    public boolean verifyOtp(String email, String code) {
        OtpData data = otpStore.get(email);
        if (data == null) return false;
        if (LocalDateTime.now().isAfter(data.expiresAt())) {
            otpStore.remove(email);
            return false;
        }
        if (MessageDigest.isEqual(data.codeHash(), hashBytes(code))) {
            otpStore.remove(email);
            return true;
        }
        if (data.attempts().incrementAndGet() >= MAX_ATTEMPTS) {
            otpStore.remove(email);
        }
        return false;
    }

    /**
     * Canal de démonstration clairement isolé : ne renvoie le code que si
     * app.otp.mock-delivery=true. Jamais appelé par l'endpoint public.
     */
    public String peekMockCode(String email) {
        if (!mockDeliveryEnabled) return null;
        OtpData data = otpStore.get(email);
        return data != null ? data.plainCode() : null;
    }

    private static byte[] hashBytes(String code) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(code.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }

    private record OtpData(byte[] codeHash, String plainCode, LocalDateTime expiresAt, AtomicInteger attempts) {}
}
