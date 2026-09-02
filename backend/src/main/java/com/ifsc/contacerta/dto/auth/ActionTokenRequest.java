package com.ifsc.contacerta.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record ActionTokenRequest(@NotBlank String token) {}
