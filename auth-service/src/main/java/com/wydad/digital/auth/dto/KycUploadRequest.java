package com.wydad.digital.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record KycUploadRequest(
        @Email @NotBlank String email,
        @NotBlank String documentType,
        @NotBlank String documentNumber,
        @NotBlank String filePath
) {}