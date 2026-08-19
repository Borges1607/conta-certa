package com.ifsc.contacerta.controller;

import com.ifsc.contacerta.dto.auth.AuthResponse;
import com.ifsc.contacerta.dto.auth.LoginRequest;
import com.ifsc.contacerta.entity.User;
import com.ifsc.contacerta.model.AccountStatus;
import com.ifsc.contacerta.model.Role;
import com.ifsc.contacerta.repository.UserRepository;
import com.ifsc.contacerta.service.AuthService;
import com.ifsc.contacerta.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AuthControllerSessionTest extends PostgresIntegrationTest {

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private AuthService authService;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private PasswordEncoder passwordEncoder;

	@Test
	void deveRotacionarRefreshTokenPelaApi() throws Exception {
		AuthResponse login = login();

		mockMvc.perform(post("/auth/refresh")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"refreshToken":"%s"}
							""".formatted(login.refreshToken())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andExpect(jsonPath("$.refreshToken").isNotEmpty())
				.andExpect(jsonPath("$.refreshToken").value(org.hamcrest.Matchers.not(login.refreshToken())));
	}

	@Test
	void deveEncerrarSessaoERejeitarMesmoJwtDepois() throws Exception {
		AuthResponse login = login();

		mockMvc.perform(post("/auth/logout")
					.header("Authorization", "Bearer " + login.accessToken()))
				.andExpect(status().isNoContent());

		mockMvc.perform(post("/auth/logout")
					.header("Authorization", "Bearer " + login.accessToken()))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
	}

	@Test
	void deveRetornarProblemDetailsParaRefreshInvalido() throws Exception {
		mockMvc.perform(post("/auth/refresh")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"refreshToken":"unknown-refresh-token"}
							"""))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
	}

	private AuthResponse login() {
		String email = "admin-" + UUID.randomUUID() + "@contacerta.local";
		User user = new User(Role.ADMIN, AccountStatus.ACTIVE, "Admin", email, null, null);
		user.initializePassword(passwordEncoder.encode("Admin123"), true);
		userRepository.saveAndFlush(user);
		return authService.login(new LoginRequest(email, "Admin123"));
	}
}
