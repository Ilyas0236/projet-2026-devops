package com.wydad.digital.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** S6 : réinitialisation de mot de passe — l'OTP prouve la possession de l'email. */
public record ResetPasswordRequest(
        @Email @NotBlank String email,
        @NotBlank @Size(min = 6, max = 6) String otpCode,
        @NotBlank @Size(min = 6, max = 100) String newPassword
) {}
