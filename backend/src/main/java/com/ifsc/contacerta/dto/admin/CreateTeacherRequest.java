package com.ifsc.contacerta.dto.admin;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateTeacherRequest(
		@NotBlank @Size(max = 160) String fullName,
		@NotBlank @Email @Size(max = 254) String email,
		@NotBlank @Size(max = 80) String registrationNumber,
		@NotNull UUID institutionId
) {
}
