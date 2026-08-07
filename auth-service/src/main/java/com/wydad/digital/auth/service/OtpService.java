package com.wydad.digital.auth.service;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OtpService {

    // Stockage temporaire en mémoire : email -> (code, expiration)
    private final Map<String, OtpData> otpStore = new ConcurrentHashMap<>();
    private final Random random = new Random();

    public String generateOtp(String email) {
        String code = String.format("%06d", random.nextInt(999999));
        otpStore.put(email, new OtpData(code, LocalDateTime.now().plusMinutes(10)));
        return code;
    }

    public boolean verifyOtp(String email, String code) {
        OtpData data = otpStore.get(email);
        if (data == null) return false;
        if (LocalDateTime.now().isAfter(data.expiresAt())) {
            otpStore.remove(email);
            return false;
        }
        boolean valid = data.code().equals(code);
        if (valid) {
            otpStore.remove(email);
        }
        return valid;
    }

    private record OtpData(String code, LocalDateTime expiresAt) {}
}