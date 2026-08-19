package com.ifsc.contacerta.service;

import com.ifsc.contacerta.dto.auth.AuthResponse;
import com.ifsc.contacerta.dto.auth.ChangePasswordRequest;
import com.ifsc.contacerta.dto.auth.LoginRequest;
import com.ifsc.contacerta.dto.auth.UserResponse;
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
import com.ifsc.contacerta.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrentUserServiceTest extends PostgresIntegrationTest {

	private static final String CURRENT_PASSWORD = "Admin123";

	@Autowired
	private CurrentUserService service;
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

	@Test
	void deveRetornarPerfilDoUsuarioAutenticado() {
		User user = user();
		CurrentUser currentUser = currentUser(login(user));

		UserResponse response = service.get(currentUser);

		assertThat(response.id()).isEqualTo(currentUser.userId());
		assertThat(response.fullName()).isEqualTo("Admin");
		assertThat(response.email()).isEqualTo(user.getEmail());
		assertThat(response.role()).isEqualTo(Role.ADMIN);
		assertThat(response.status()).isEqualTo(AccountStatus.ACTIVE);
		assertThat(response.institutionId()).isNull();
		assertThat(response.mustChangePassword()).isTrue();
	}

	@Test
	void deveTrocarSenhaERevogarTodasAsSessoesDoUsuario() {
		User user = user();
		AuthResponse firstLogin = login(user);
		AuthResponse secondLogin = login(user);
		CurrentUser currentUser = currentUser(firstLogin);
		Set<UUID> userSessionIds = Set.of(
				currentUser.sessionId(),
				jwtService.parse(secondLogin.accessToken()).sessionId()
		);
		AuthResponse anotherUserLogin = login(user());
		UUID anotherSessionId = jwtService.parse(anotherUserLogin.accessToken()).sessionId();

		service.changePassword(currentUser, new ChangePasswordRequest(CURRENT_PASSWORD, "NovaSenha456"));

		User reloaded = userRepository.findById(user.getId()).orElseThrow();
		assertThat(passwordEncoder.matches("NovaSenha456", reloaded.getPasswordHash())).isTrue();
		assertThat(reloaded.isMustChangePassword()).isFalse();
		assertThat(sessionRepository.findAll().stream()
				.filter(session -> userSessionIds.contains(session.getId())))
				.allMatch(session -> session.getRevokedAt() != null);
		assertThat(tokenRepository.findAll().stream()
				.filter(token -> userSessionIds.contains(token.getSession().getId())))
				.allMatch(token -> token.getRevokedAt() != null);
		assertThat(sessionRepository.findById(anotherSessionId)).isPresent().get()
				.extracting(AuthSession::getRevokedAt).isNull();
	}

	@Test
	void deveRejeitarSenhaAtualIncorretaSemRevogarSessao() {
		User user = user();
		CurrentUser currentUser = currentUser(login(user));

		assertError(
				currentUser,
				new ChangePasswordRequest("Errada123", "NovaSenha456"),
				"CURRENT_PASSWORD_INVALID"
		);

		assertThat(sessionRepository.findById(currentUser.sessionId())).isPresent().get()
				.extracting(AuthSession::getRevokedAt).isNull();
	}

	@Test
	void deveRejeitarReusoDaSenhaAtual() {
		User user = user();
		CurrentUser currentUser = currentUser(login(user));

		assertError(
				currentUser,
				new ChangePasswordRequest(CURRENT_PASSWORD, CURRENT_PASSWORD),
				"PASSWORD_REUSE_NOT_ALLOWED"
		);
	}

	@Test
	void deveAplicarPoliticaNaNovaSenha() {
		User user = user();
		CurrentUser currentUser = currentUser(login(user));

		assertError(
				currentUser,
				new ChangePasswordRequest(CURRENT_PASSWORD, "curta1"),
				"INVALID_PASSWORD"
		);
	}

	private void assertError(CurrentUser currentUser, ChangePasswordRequest request, String code) {
		assertThatThrownBy(() -> service.changePassword(currentUser, request))
				.isInstanceOfSatisfying(ApiException.class,
						exception -> assertThat(exception.getCode()).isEqualTo(code));
	}

	private User user() {
		String email = "admin-" + UUID.randomUUID() + "@contacerta.local";
		User user = new User(Role.ADMIN, AccountStatus.ACTIVE, "Admin", email, null, null);
		user.initializePassword(passwordEncoder.encode(CURRENT_PASSWORD), true);
		return userRepository.saveAndFlush(user);
	}

	private AuthResponse login(User user) {
		return authService.login(new LoginRequest(user.getEmail(), CURRENT_PASSWORD));
	}

	private CurrentUser currentUser(AuthResponse response) {
		var claims = jwtService.parse(response.accessToken());
		return new CurrentUser(claims.userId(), claims.role(), claims.sessionId());
	}
}
