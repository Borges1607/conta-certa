package com.ifsc.contacerta.exception;

import org.springframework.http.HttpStatus;

public class RefreshTokenReusedException extends ApiException {

	public RefreshTokenReusedException() {
		super(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_REUSED", "Refresh token was already used.");
	}
}
