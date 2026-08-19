package com.ifsc.contacerta.dto.auth;

public record AuthResponse(
		String accessToken,
		String refreshToken,
		long accessTokenExpiresIn,
		long refreshTokenExpiresIn,
		UserResponse user
) {
}
