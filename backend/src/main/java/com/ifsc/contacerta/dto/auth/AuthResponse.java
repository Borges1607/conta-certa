package com.ifsc.contacerta.dto.auth;

import com.fasterxml.jackson.annotation.JsonInclude;

public record AuthResponse(
		String accessToken,
		String refreshToken,
		String tokenType,
		long accessExpiresIn,
		long refreshExpiresIn,
		@JsonInclude(JsonInclude.Include.NON_NULL)
		UserResponse user
) {
}
