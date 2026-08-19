package com.ifsc.contacerta.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
		@NotBlank @Size(min = 3, max = 160) String fullName
) {
	public UpdateProfileRequest {
		if (fullName != null) {
			fullName = fullName.trim();
		}
	}
}
