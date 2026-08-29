package com.ifsc.contacerta.config;

import com.ifsc.contacerta.entity.AuthSession;
import com.ifsc.contacerta.entity.User;
import com.ifsc.contacerta.model.AccountStatus;
import com.ifsc.contacerta.model.Role;
import com.ifsc.contacerta.repository.AuthSessionRepository;
import com.ifsc.contacerta.repository.UserRepository;
import com.ifsc.contacerta.security.CurrentUser;
import com.ifsc.contacerta.security.JwtService;
import com.ifsc.contacerta.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Import(SecurityConfigTest.ProtectedTestController.class)
class SecurityConfigTest extends PostgresIntegrationTest {

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private AuthSessionRepository sessionRepository;
	@Autowired
	private JwtService jwtService;

	@Test
	void devePermitirListagemPublicaDeInstituicoes() throws Exception {
		mockMvc.perform(get("/institutions/options"))
				.andExpect(status().isOk());
	}

	@Test
	void deveExigirTokenDeAcessoNasRotasProtegidas() throws Exception {
		mockMvc.perform(get("/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentType("application/problem+json"))
				.andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
	}

	@Test
	void deveRestringirGamificacaoAoAluno() throws Exception {
		AuthSession session = activeSession(AccountStatus.ACTIVE);
		String token = jwtService.issue(session.getUser().getId(), Role.ADMIN, session.getId());

		mockMvc.perform(get("/student/rooms/{roomId}/ranking", UUID.randomUUID())
					.header("Authorization", "Bearer " + token))
				.andExpect(status().isForbidden());
	}

	@Test
	void deveAutenticarTokenVinculadoASessaoAtiva() throws Exception {
		AuthSession session = activeSession(AccountStatus.ACTIVE);
		String token = jwtService.issue(session.getUser().getId(), Role.ADMIN, session.getId());

		mockMvc.perform(get("/test/current-user").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.userId").value(session.getUser().getId().toString()))
				.andExpect(jsonPath("$.role").value("ADMIN"))
				.andExpect(jsonPath("$.sessionId").value(session.getId().toString()));
	}

	@Test
	void deveRejeitarTokenVinculadoASessaoRevogada() throws Exception {
		AuthSession session = activeSession(AccountStatus.ACTIVE);
		session.revoke(Instant.now());
		sessionRepository.saveAndFlush(session);
		String token = jwtService.issue(session.getUser().getId(), Role.ADMIN, session.getId());

		mockMvc.perform(get("/test/current-user").header("Authorization", "Bearer " + token))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
	}

	@Test
	void deveRejeitarTokenDeUsuarioInativo() throws Exception {
		AuthSession session = activeSession(AccountStatus.INACTIVE);
		String token = jwtService.issue(session.getUser().getId(), Role.ADMIN, session.getId());

		mockMvc.perform(get("/test/current-user").header("Authorization", "Bearer " + token))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
	}

	@Test
	void deveRejeitarTokenVinculadoASessaoExpirada() throws Exception {
		Instant now = Instant.now();
		AuthSession session = session(
				AccountStatus.ACTIVE,
				now.minus(1, ChronoUnit.MINUTES),
				now.minus(1, ChronoUnit.DAYS)
		);
		String token = jwtService.issue(session.getUser().getId(), Role.ADMIN, session.getId());

		mockMvc.perform(get("/test/current-user").header("Authorization", "Bearer " + token))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
	}

	@Test
	void deveRejeitarTokenComPapelDiferenteDoUsuarioDaSessao() throws Exception {
		AuthSession session = activeSession(AccountStatus.ACTIVE);
		String token = jwtService.issue(session.getUser().getId(), Role.TEACHER, session.getId());

		mockMvc.perform(get("/test/current-user").header("Authorization", "Bearer " + token))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
	}

	@Test
	void deveRejeitarTokenDeSessaoInexistente() throws Exception {
		String token = jwtService.issue(UUID.randomUUID(), Role.ADMIN, UUID.randomUUID());

		mockMvc.perform(get("/test/current-user").header("Authorization", "Bearer " + token))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
	}

	private AuthSession activeSession(AccountStatus status) {
		Instant now = Instant.now();
		return session(status, now.plus(1, ChronoUnit.DAYS), now);
	}

	private AuthSession session(AccountStatus status, Instant expiresAt, Instant createdAt) {
		String email = "admin-" + UUID.randomUUID() + "@example.com";
		User user = userRepository.saveAndFlush(new User(Role.ADMIN, status, "Admin", email, null, null));
		return sessionRepository.saveAndFlush(new AuthSession(user, expiresAt, createdAt));
	}

	@RestController
	static class ProtectedTestController {

		@GetMapping("/test/current-user")
		CurrentUser currentUser(@AuthenticationPrincipal CurrentUser currentUser) {
			return currentUser;
		}
	}
}
