package com.ifsc.contacerta.controller;

import com.ifsc.contacerta.dto.auth.AuthResponse;
import com.ifsc.contacerta.dto.auth.LoginRequest;
import com.ifsc.contacerta.entity.Institution;
import com.ifsc.contacerta.entity.User;
import com.ifsc.contacerta.model.AccountStatus;
import com.ifsc.contacerta.model.Role;
import com.ifsc.contacerta.repository.UserRepository;
import com.ifsc.contacerta.repository.InstitutionRepository;
import com.ifsc.contacerta.service.AuthService;
import com.ifsc.contacerta.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class MeControllerTest extends PostgresIntegrationTest {

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private AuthService authService;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private InstitutionRepository institutionRepository;
	@Autowired
	private PasswordEncoder passwordEncoder;

	@Test
	void deveRetornarPerfilSemDadosSensiveis() throws Exception {
		Institution institution = institutionRepository.saveAndFlush(new Institution(
				"IFSC", "00000000000191", "contato@ifsc.edu.br", "+5548999999999", true
		));
		User user = user(Role.STUDENT, "2026001", institution);
		AuthResponse login = login(user);

		mockMvc.perform(get("/me").header("Authorization", "Bearer " + login.accessToken()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(user.getId().toString()))
				.andExpect(jsonPath("$.email").value(user.getEmail()))
				.andExpect(jsonPath("$.role").value("STUDENT"))
				.andExpect(jsonPath("$.registrationNumber").value("2026001"))
				.andExpect(jsonPath("$.institution.id").value(institution.getId().toString()))
				.andExpect(jsonPath("$.institution.name").value("IFSC"))
				.andExpect(jsonPath("$.institution.cnpj").value("00000000000191"))
				.andExpect(jsonPath("$.institution.contactEmail").value("contato@ifsc.edu.br"))
				.andExpect(jsonPath("$.institution.contactPhone").value("+5548999999999"))
				.andExpect(jsonPath("$.institution.active").value(true))
				.andExpect(jsonPath("$.emailVerified").value(false))
				.andExpect(jsonPath("$.mustChangePassword").value(true))
				.andExpect(jsonPath("$.passwordHash").doesNotExist());
	}

	@Test
	void deveExigirBearerParaConsultarPerfil() throws Exception {
		mockMvc.perform(get("/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
	}

	@Test
	void deveTrocarSenhaEInvalidarJwtAnterior() throws Exception {
		User user = user();
		AuthResponse login = login(user);

		mockMvc.perform(post("/me/change-password")
					.header("Authorization", "Bearer " + login.accessToken())
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"currentPassword":"Admin123","newPassword":"NovaSenha456"}
							"""))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/me").header("Authorization", "Bearer " + login.accessToken()))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));

		authService.login(new LoginRequest(user.getEmail(), "NovaSenha456"));
	}

	@Test
	void deveRetornarErroDeDominioParaSenhaAtualIncorreta() throws Exception {
		AuthResponse login = login(user());

		mockMvc.perform(post("/me/change-password")
					.header("Authorization", "Bearer " + login.accessToken())
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"currentPassword":"Errada123","newPassword":"NovaSenha456"}
							"""))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_INVALID"));
	}

	private User user() {
		return user(Role.ADMIN, null, null);
	}

	private User user(Role role, String registrationNumber, Institution institution) {
		String email = "admin-" + UUID.randomUUID() + "@contacerta.local";
		User user = new User(role, AccountStatus.ACTIVE, "Admin", email, registrationNumber, institution);
		user.initializePassword(passwordEncoder.encode("Admin123"), true);
		return userRepository.saveAndFlush(user);
	}

	private AuthResponse login(User user) {
		return authService.login(new LoginRequest(user.getEmail(), "Admin123"));
	}
}
