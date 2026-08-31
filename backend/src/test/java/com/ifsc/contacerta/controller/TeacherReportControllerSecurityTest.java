package com.ifsc.contacerta.controller;

import com.ifsc.contacerta.dto.auth.AuthResponse;
import com.ifsc.contacerta.dto.auth.LoginRequest;
import com.ifsc.contacerta.entity.Institution;
import com.ifsc.contacerta.entity.Room;
import com.ifsc.contacerta.entity.User;
import com.ifsc.contacerta.model.AccountStatus;
import com.ifsc.contacerta.model.Grade;
import com.ifsc.contacerta.model.Role;
import com.ifsc.contacerta.repository.InstitutionRepository;
import com.ifsc.contacerta.repository.RoomRepository;
import com.ifsc.contacerta.repository.UserRepository;
import com.ifsc.contacerta.service.AuthService;
import com.ifsc.contacerta.service.JoinCodeHasher;
import com.ifsc.contacerta.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class TeacherReportControllerSecurityTest extends PostgresIntegrationTest {

	@Autowired private MockMvc mockMvc;
	@Autowired private AuthService authService;
	@Autowired private InstitutionRepository institutionRepository;
	@Autowired private UserRepository userRepository;
	@Autowired private RoomRepository roomRepository;
	@Autowired private PasswordEncoder passwordEncoder;

	private final JoinCodeHasher joinCodeHasher = new JoinCodeHasher();

	@Test
	void deveExigirBearerParaRelatoriosDoProfessor() throws Exception {
		mockMvc.perform(get("/teacher/reports/overview").param("roomId", UUID.randomUUID().toString()))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
	}

	@Test
	void deveImpedirAlunoDeAcessarRelatoriosDoProfessor() throws Exception {
		Institution institution = institution();
		User student = user(Role.STUDENT, institution);

		mockMvc.perform(get("/teacher/reports/overview")
					.header("Authorization", bearer(login(student)))
					.param("roomId", UUID.randomUUID().toString()))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("TEACHER_REQUIRED"));
	}

	@Test
	void deveOcultarSalaDeOutroProfessor() throws Exception {
		Institution institution = institution();
		User teacher = user(Role.TEACHER, institution);
		User otherTeacher = user(Role.TEACHER, institution);
		Room foreignRoom = room(otherTeacher, institution);

		mockMvc.perform(get("/teacher/reports/overview")
					.header("Authorization", bearer(login(teacher)))
					.param("roomId", foreignRoom.getId().toString())
					.param("period", "ALL"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("ROOM_NOT_FOUND"));
	}

	@Test
	void deveValidarIntervaloParametrosEPaginacao() throws Exception {
		Institution institution = institution();
		User teacher = user(Role.TEACHER, institution);
		Room room = room(teacher, institution);
		String authorization = bearer(login(teacher));

		mockMvc.perform(get("/teacher/reports/overview")
					.header("Authorization", authorization)
					.param("roomId", room.getId().toString())
					.param("from", "2026-08-20T00:00:00Z")
					.param("to", "2026-08-01T00:00:00Z"))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

		mockMvc.perform(get("/teacher/reports/overview")
					.header("Authorization", authorization)
					.param("roomId", "invalid-uuid"))
				.andExpect(status().isBadRequest());

		mockMvc.perform(get("/teacher/reports/students")
					.header("Authorization", authorization)
					.param("roomId", room.getId().toString())
					.param("size", "101"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("BAD_REQUEST"));
	}

	private Institution institution() {
		return institutionRepository.saveAndFlush(new Institution(
				"IFSC " + UUID.randomUUID(), randomCnpj(), "contato@example.com", "48999990000", true
		));
	}

	private User user(Role role, Institution institution) {
		String email = role.name().toLowerCase() + "-" + UUID.randomUUID() + "@contacerta.local";
		User user = new User(
				role, AccountStatus.ACTIVE, "Usuário Exemplo", email,
				"REG-" + UUID.randomUUID(), institution
		);
		user.initializePassword(passwordEncoder.encode("Senha123"), false);
		return userRepository.saveAndFlush(user);
	}

	private Room room(User teacher, Institution institution) {
		String joinCode = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
		return roomRepository.saveAndFlush(new Room(
				"Sala", "Descrição", Grade.HIGH_SCHOOL_1, List.of("Porcentagem"), 60,
				joinCode, joinCodeHasher.hash(joinCode), teacher, institution
		));
	}

	private AuthResponse login(User user) {
		return authService.login(new LoginRequest(user.getEmail(), "Senha123"));
	}

	private String bearer(AuthResponse response) {
		return "Bearer " + response.accessToken();
	}

	private String randomCnpj() {
		return "%014d".formatted(Math.abs(UUID.randomUUID().getMostSignificantBits()) % 100000000000000L);
	}
}
