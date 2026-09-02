package com.ifsc.contacerta.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record StudentRegistrationRequest(
		@NotBlank @Size(max = 160) String fullName,
		@NotBlank @Email @Size(max = 254) String email,
		@NotBlank @Size(min = 8, max = 72) String password,
		@NotBlank @Size(max = 80) String registrationNumber,
		@NotNull UUID institutionId
) {}
