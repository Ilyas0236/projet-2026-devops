package com.wydad.digital.auth.dto;

import jakarta.validation.constraints.NotNull;

public record RevokeSessionRequest(
        @NotNull Long sessionId
) {}