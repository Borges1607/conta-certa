package com.ifsc.contacerta.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record PatchTeacherRequest(
		@NotBlank @Size(max = 160) String fullName,
		@NotBlank @Size(max = 80) String registrationNumber,
		@NotNull UUID institutionId,
		@NotNull Long version
) {
}
