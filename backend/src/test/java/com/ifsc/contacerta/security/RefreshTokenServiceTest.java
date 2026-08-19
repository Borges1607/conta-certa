package com.ifsc.contacerta.security;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenServiceTest {

	private final RefreshTokenService service = new RefreshTokenService(new SecureRandom());

	@Test
	void deveGerarRefreshComEntropiaEHashSeparado() {
		RefreshTokenService.GeneratedRefreshToken first = service.generate();
		RefreshTokenService.GeneratedRefreshToken second = service.generate();

		assertThat(first.plainText()).hasSize(43).doesNotContain("=");
		assertThat(first.hash()).hasSize(64).isEqualTo(service.hash(first.plainText()));
		assertThat(second.plainText()).isNotEqualTo(first.plainText());
		assertThat(second.hash()).isNotEqualTo(first.hash());
	}
}
