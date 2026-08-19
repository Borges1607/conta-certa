package com.ifsc.contacerta.controller;

import com.ifsc.contacerta.entity.User;
import com.ifsc.contacerta.model.AccountStatus;
import com.ifsc.contacerta.model.Role;
import com.ifsc.contacerta.repository.UserRepository;
import com.ifsc.contacerta.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AuthControllerLoginTest extends PostgresIntegrationTest {

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private PasswordEncoder passwordEncoder;

	@Test
	void deveRetornarTokensEUsuarioSemDadosSensiveis() throws Exception {
		User user = activeUser();

		mockMvc.perform(post("/auth/login")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"email":"%s","password":"Admin123"}
							""".formatted(user.getEmail())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andExpect(jsonPath("$.refreshToken").isNotEmpty())
				.andExpect(jsonPath("$.tokenType").value("Bearer"))
				.andExpect(jsonPath("$.accessExpiresIn").value(900))
				.andExpect(jsonPath("$.refreshExpiresIn").value(604800))
				.andExpect(jsonPath("$.user.id").value(user.getId().toString()))
				.andExpect(jsonPath("$.user.registrationNumber").value(org.hamcrest.Matchers.nullValue()))
				.andExpect(jsonPath("$.user.institution").value(org.hamcrest.Matchers.nullValue()))
				.andExpect(jsonPath("$.user.emailVerified").value(false))
				.andExpect(jsonPath("$.user.mustChangePassword").value(true))
				.andExpect(jsonPath("$.user.passwordHash").doesNotExist())
				.andExpect(jsonPath("$.passwordHash").doesNotExist());
	}

	@Test
	void deveRetornarProblemDetailsParaCredenciaisInvalidas() throws Exception {
		mockMvc.perform(post("/auth/login")
					.header("X-Trace-Id", "login-test")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"email":"unknown@example.com","password":"Errada123"}
							"""))
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentType("application/problem+json"))
				.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
				.andExpect(jsonPath("$.traceId").value("login-test"));
	}

	private User activeUser() {
		String email = "admin-" + UUID.randomUUID() + "@contacerta.local";
		User user = new User(Role.ADMIN, AccountStatus.ACTIVE, "Admin", email, null, null);
		user.initializePassword(passwordEncoder.encode("Admin123"), true);
		return userRepository.saveAndFlush(user);
	}
}
