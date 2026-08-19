package com.ifsc.contacerta.service;

import com.ifsc.contacerta.dto.auth.AuthResponse;
import com.ifsc.contacerta.dto.auth.LoginRequest;
import com.ifsc.contacerta.dto.auth.RefreshRequest;
import com.ifsc.contacerta.entity.AuthSession;
import com.ifsc.contacerta.entity.RefreshToken;
import com.ifsc.contacerta.entity.User;
import com.ifsc.contacerta.exception.ApiException;
import com.ifsc.contacerta.model.AccountStatus;
import com.ifsc.contacerta.model.Role;
import com.ifsc.contacerta.repository.AuthSessionRepository;
import com.ifsc.contacerta.repository.RefreshTokenRepository;
import com.ifsc.contacerta.repository.UserRepository;
import com.ifsc.contacerta.security.CurrentUser;
import com.ifsc.contacerta.security.JwtService;
import com.ifsc.contacerta.security.RefreshTokenService;
import com.ifsc.contacerta.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthServiceRefreshTest extends PostgresIntegrationTest {

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
	void deveRotacionarRefreshEInvalidarAnterior() {
		AuthResponse login = login();
		UUID sessionId = jwtService.parse(login.accessToken()).sessionId();

		AuthResponse refreshed = authService.refresh(new RefreshRequest(login.refreshToken()));

		assertThat(refreshed.refreshToken()).isNotEqualTo(login.refreshToken());
		assertThat(refreshed.accessToken()).isNotEqualTo(login.accessToken());
		List<RefreshToken> tokens = tokensOf(sessionId);
		assertThat(tokens).hasSize(2);
		assertThat(tokens.stream()
				.filter(token -> token.getTokenHash().equals(refreshTokenService.hash(login.refreshToken())))
				.findFirst()).isPresent().get().extracting(RefreshToken::getRotatedAt).isNotNull();
		assertThat(tokens).anyMatch(token -> token.getTokenHash()
				.equals(refreshTokenService.hash(refreshed.refreshToken())));
	}

	@Test
	void deveRevogarSessaoQuandoRefreshRotacionadoForReutilizado() {
		AuthResponse login = login();
		UUID sessionId = jwtService.parse(login.accessToken()).sessionId();
		authService.refresh(new RefreshRequest(login.refreshToken()));

		assertThatThrownBy(() -> authService.refresh(new RefreshRequest(login.refreshToken())))
				.isInstanceOfSatisfying(ApiException.class,
						exception -> assertThat(exception.getCode()).isEqualTo("REFRESH_TOKEN_REUSED"));

		assertThat(sessionRepository.findById(sessionId)).isPresent().get()
				.extracting(AuthSession::getRevokedAt).isNotNull();
		assertThat(tokensOf(sessionId)).allMatch(token -> token.getRevokedAt() != null);
	}

	@Test
	void deveRejeitarRefreshDesconhecido() {
		assertInvalidRefresh("unknown-refresh-token");
	}

	@Test
	void deveRejeitarRefreshExpirado() {
		Instant now = Instant.now();
		User user = user(AccountStatus.ACTIVE);
		AuthSession session = sessionRepository.saveAndFlush(new AuthSession(
				user,
				now.minus(1, ChronoUnit.HOURS),
				now.minus(8, ChronoUnit.DAYS)
		));
		String plainText = "expired-refresh-token";
		tokenRepository.saveAndFlush(new RefreshToken(
				session,
				refreshTokenService.hash(plainText),
				now.minus(1, ChronoUnit.HOURS),
				now.minus(8, ChronoUnit.DAYS)
		));

		assertInvalidRefresh(plainText);
	}

	@Test
	void deveRejeitarRefreshRevogado() {
		AuthResponse login = login();
		RefreshToken token = tokenByPlainText(login.refreshToken());
		token.revoke(Instant.now());
		tokenRepository.saveAndFlush(token);

		assertInvalidRefresh(login.refreshToken());
	}

	@Test
	void deveRejeitarRefreshExpiradoMesmoComSessaoAtiva() {
		Instant now = Instant.now();
		User user = user(AccountStatus.ACTIVE);
		AuthSession session = sessionRepository.saveAndFlush(new AuthSession(
				user,
				now.plus(7, ChronoUnit.DAYS),
				now.minus(8, ChronoUnit.DAYS)
		));
		String plainText = "expired-token-active-session";
		tokenRepository.saveAndFlush(new RefreshToken(
				session,
				refreshTokenService.hash(plainText),
				now.minus(1, ChronoUnit.HOURS),
				now.minus(8, ChronoUnit.DAYS)
		));

		assertInvalidRefresh(plainText);
	}

	@Test
	void deveRejeitarRefreshDeSessaoRevogada() {
		AuthResponse login = login();
		UUID sessionId = jwtService.parse(login.accessToken()).sessionId();
		AuthSession session = sessionRepository.findById(sessionId).orElseThrow();
		session.revoke(Instant.now());
		sessionRepository.saveAndFlush(session);

		assertInvalidRefresh(login.refreshToken());
	}

	@Test
	void deveRejeitarRefreshDeUsuarioInativo() {
		Instant now = Instant.now();
		User user = user(AccountStatus.INACTIVE);
		AuthSession session = sessionRepository.saveAndFlush(new AuthSession(
				user,
				now.plus(7, ChronoUnit.DAYS),
				now
		));
		String plainText = "inactive-user-refresh-token";
		tokenRepository.saveAndFlush(new RefreshToken(
				session,
				refreshTokenService.hash(plainText),
				now.plus(7, ChronoUnit.DAYS),
				now
		));

		assertInvalidRefresh(plainText);
	}

	@Test
	void devePermitirSomenteUmaRotacaoConcorrente() throws Exception {
		AuthResponse login = login();
		UUID sessionId = jwtService.parse(login.accessToken()).sessionId();
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);

		try (var executor = Executors.newFixedThreadPool(2)) {
			var attempts = List.of(1, 2).stream()
					.map(ignored -> executor.submit(() -> {
						ready.countDown();
						start.await();
						try {
							return (Object) authService.refresh(new RefreshRequest(login.refreshToken()));
						} catch (ApiException exception) {
							return exception;
						}
					}))
					.toList();
			ready.await();
			start.countDown();
			List<Object> results = attempts.stream().map(future -> {
				try {
					return future.get();
				} catch (Exception exception) {
					throw new AssertionError(exception);
				}
			}).toList();

			assertThat(results).filteredOn(AuthResponse.class::isInstance).hasSize(1);
			assertThat(results).filteredOn(ApiException.class::isInstance)
					.singleElement()
					.satisfies(result -> assertThat(((ApiException) result).getCode())
							.isEqualTo("REFRESH_TOKEN_REUSED"));
		}
		assertThat(tokensOf(sessionId)).hasSize(2);
		assertThat(sessionRepository.findById(sessionId)).isPresent().get()
				.extracting(AuthSession::getRevokedAt).isNotNull();
	}

	@Test
	void deveEncerrarSomenteASessaoAtualDeFormaIdempotente() {
		User user = user(AccountStatus.ACTIVE);
		AuthResponse first = authService.login(new LoginRequest(user.getEmail(), PASSWORD));
		AuthResponse second = authService.login(new LoginRequest(user.getEmail(), PASSWORD));
		var firstClaims = jwtService.parse(first.accessToken());
		var secondClaims = jwtService.parse(second.accessToken());
		CurrentUser currentUser = new CurrentUser(firstClaims.userId(), firstClaims.role(), firstClaims.sessionId());

		authService.logout(currentUser);
		authService.logout(currentUser);

		assertThat(sessionRepository.findById(firstClaims.sessionId())).isPresent().get()
				.extracting(AuthSession::getRevokedAt).isNotNull();
		assertThat(tokensOf(firstClaims.sessionId())).allMatch(token -> token.getRevokedAt() != null);
		assertThat(sessionRepository.findById(secondClaims.sessionId())).isPresent().get()
				.extracting(AuthSession::getRevokedAt).isNull();
		assertThat(tokensOf(secondClaims.sessionId())).allMatch(token -> token.getRevokedAt() == null);
	}

	private AuthResponse login() {
		User user = user(AccountStatus.ACTIVE);
		return authService.login(new LoginRequest(user.getEmail(), PASSWORD));
	}

	private User user(AccountStatus status) {
		String email = "admin-" + UUID.randomUUID() + "@contacerta.local";
		User user = new User(Role.ADMIN, status, "Admin", email, null, null);
		user.initializePassword(passwordEncoder.encode(PASSWORD), true);
		return userRepository.saveAndFlush(user);
	}

	private void assertInvalidRefresh(String plainText) {
		assertThatThrownBy(() -> authService.refresh(new RefreshRequest(plainText)))
				.isInstanceOfSatisfying(ApiException.class,
						exception -> assertThat(exception.getCode()).isEqualTo("INVALID_REFRESH_TOKEN"));
	}

	private RefreshToken tokenByPlainText(String plainText) {
		String hash = refreshTokenService.hash(plainText);
		return tokenRepository.findAll().stream()
				.filter(token -> token.getTokenHash().equals(hash))
				.findFirst()
				.orElseThrow();
	}

	private List<RefreshToken> tokensOf(UUID sessionId) {
		return tokenRepository.findAll().stream()
				.filter(token -> token.getSession().getId().equals(sessionId))
				.toList();
	}
}
