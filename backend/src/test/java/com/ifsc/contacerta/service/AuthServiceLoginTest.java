package com.ifsc.contacerta.service;

import com.ifsc.contacerta.dto.auth.AuthResponse;
import com.ifsc.contacerta.dto.auth.LoginRequest;
import com.ifsc.contacerta.entity.User;
import com.ifsc.contacerta.exception.ApiException;
import com.ifsc.contacerta.model.AccountStatus;
import com.ifsc.contacerta.model.Role;
import com.ifsc.contacerta.repository.AuthSessionRepository;
import com.ifsc.contacerta.repository.RefreshTokenRepository;
import com.ifsc.contacerta.repository.UserRepository;
import com.ifsc.contacerta.security.JwtService;
import com.ifsc.contacerta.security.RefreshTokenService;
import com.ifsc.contacerta.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthServiceLoginTest extends PostgresIntegrationTest {

	private static final String PASSWORD = "Admin123";

	@Autowired
	private AuthService authService;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private AuthSessionRepository sessionRepository;
	@Autowired
	private RefreshTokenRepository tokenRepository;
	@Autowired
	private PasswordEncoder passwordEncoder;
	@Autowired
	private JwtService jwtService;
	@Autowired
	private RefreshTokenService refreshTokenService;

	@Test
	void deveCriarSessaoETokensParaCredenciaisValidas() {
		User user = user(AccountStatus.ACTIVE);
		long sessionsBefore = sessionRepository.count();
		long tokensBefore = tokenRepository.count();

		AuthResponse response = authService.login(new LoginRequest("  " + user.getEmail().toUpperCase() + "  ", PASSWORD));

		assertThat(response.accessToken()).isNotBlank();
		assertThat(response.refreshToken()).isNotBlank();
		assertThat(response.tokenType()).isEqualTo("Bearer");
		assertThat(response.accessExpiresIn()).isEqualTo(900);
		assertThat(response.refreshExpiresIn()).isEqualTo(604800);
		assertThat(response.user().id()).isEqualTo(user.getId());
		assertThat(response.user().mustChangePassword()).isTrue();
		assertThat(jwtService.parse(response.accessToken()).userId()).isEqualTo(user.getId());
		assertThat(sessionRepository.count()).isEqualTo(sessionsBefore + 1);
		assertThat(tokenRepository.count()).isEqualTo(tokensBefore + 1);
		assertThat(tokenRepository.findAll().stream()
				.anyMatch(token -> token.getTokenHash().equals(refreshTokenService.hash(response.refreshToken()))))
				.isTrue();
	}

	@Test
	void deveRetornarErroGenericoParaEmailDesconhecido() {
		assertInvalidCredentials(new LoginRequest(uniqueEmail(), PASSWORD));
	}

	@Test
	void deveRetornarMesmoErroParaSenhaIncorreta() {
		User user = user(AccountStatus.ACTIVE);

		assertInvalidCredentials(new LoginRequest(user.getEmail(), "Errada123"));
	}

	@Test
	void deveRetornarMesmoErroParaUsuarioInativo() {
		User user = user(AccountStatus.INACTIVE);

		assertInvalidCredentials(new LoginRequest(user.getEmail(), PASSWORD));
	}

	private void assertInvalidCredentials(LoginRequest request) {
		long sessionsBefore = sessionRepository.count();
		assertThatThrownBy(() -> authService.login(request))
				.isInstanceOfSatisfying(ApiException.class, exception -> {
					assertThat(exception.getCode()).isEqualTo("INVALID_CREDENTIALS");
					assertThat(exception.getMessage()).doesNotContain(request.password());
				});
		assertThat(sessionRepository.count()).isEqualTo(sessionsBefore);
	}

	private User user(AccountStatus status) {
		User user = new User(Role.ADMIN, status, "Admin", uniqueEmail(), null, null);
		user.initializePassword(passwordEncoder.encode(PASSWORD), true);
		return userRepository.saveAndFlush(user);
	}

	private String uniqueEmail() {
		return "admin-" + UUID.randomUUID() + "@contacerta.local";
	}
}
