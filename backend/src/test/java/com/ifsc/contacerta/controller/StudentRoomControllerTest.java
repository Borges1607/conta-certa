package com.ifsc.contacerta.controller;

import com.ifsc.contacerta.dto.auth.AuthResponse;
import com.ifsc.contacerta.dto.auth.LoginRequest;
import com.ifsc.contacerta.entity.Institution;
import com.ifsc.contacerta.entity.Room;
import com.ifsc.contacerta.entity.RoomMembership;
import com.ifsc.contacerta.entity.User;
import com.ifsc.contacerta.model.AccountStatus;
import com.ifsc.contacerta.model.Grade;
import com.ifsc.contacerta.model.Role;
import com.ifsc.contacerta.repository.InstitutionRepository;
import com.ifsc.contacerta.repository.RoomMembershipRepository;
import com.ifsc.contacerta.repository.RoomRepository;
import com.ifsc.contacerta.repository.UserRepository;
import com.ifsc.contacerta.service.AuthService;
import com.ifsc.contacerta.service.JoinCodeHasher;
import com.ifsc.contacerta.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class StudentRoomControllerTest extends PostgresIntegrationTest {

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private AuthService authService;
	@Autowired
	private InstitutionRepository institutionRepository;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private RoomRepository roomRepository;
	@Autowired
	private RoomMembershipRepository membershipRepository;
	@Autowired
	private PasswordEncoder passwordEncoder;

	private final JoinCodeHasher joinCodeHasher = new JoinCodeHasher();

	@Test
	void deveExigirBearerParaRotasDeSalasDoAluno() throws Exception {
		mockMvc.perform(get("/student/rooms"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
	}

	@Test
	void deveImpedirProfessorDeAcessarRotasDoAluno() throws Exception {
		AuthResponse login = login(user(Role.TEACHER, institution()));

		mockMvc.perform(get("/student/rooms").header("Authorization", bearer(login)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("STUDENT_REQUIRED"));
	}

	@Test
	void deveListarSomenteSalasComMatriculaAtivaSemExporCodigo() throws Exception {
		Institution institution = institution();
		User teacher = user(Role.TEACHER, institution);
		User student = user(Role.STUDENT, institution);
		Room activeRoom = room("Sala ativa", "PQR890", teacher, institution);
		Room removedRoom = room("Sala removida", "STU123", teacher, institution);
		membershipRepository.saveAndFlush(new RoomMembership(activeRoom, student));
		RoomMembership removedMembership = membershipRepository.saveAndFlush(new RoomMembership(removedRoom, student));
		removedMembership.remove(teacher);
		membershipRepository.saveAndFlush(removedMembership);
		AuthResponse login = login(student);

		mockMvc.perform(get("/student/rooms").header("Authorization", bearer(login)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value(activeRoom.getId().toString()))
				.andExpect(jsonPath("$[0].membershipStatus").value("ACTIVE"))
				.andExpect(jsonPath("$[0].progressPercent").value(0))
				.andExpect(jsonPath("$[0].joinCode").doesNotExist());
	}

	@Test
	void deveMatricularAlunoPorCodigoNormalizado() throws Exception {
		Institution institution = institution();
		User teacher = user(Role.TEACHER, institution);
		User student = user(Role.STUDENT, institution);
		Room room = room("Sala de matemática", "VWX456", teacher, institution);
		AuthResponse login = login(student);

		mockMvc.perform(post("/student/rooms/join")
					.header("Authorization", bearer(login))
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"code\":\" vwx456 \"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(room.getId().toString()))
				.andExpect(jsonPath("$.membershipStatus").value("ACTIVE"))
				.andExpect(jsonPath("$.joinCode").doesNotExist());
	}

	@Test
	void deveRetornarErroParaCodigoInvalidoEInstituicaoDiferente() throws Exception {
		Institution roomInstitution = institution();
		Institution otherInstitution = institution();
		User teacher = user(Role.TEACHER, roomInstitution);
		User student = user(Role.STUDENT, otherInstitution);
		room("Sala restrita", "YZA789", teacher, roomInstitution);
		AuthResponse login = login(student);

		mockMvc.perform(post("/student/rooms/join")
					.header("Authorization", bearer(login))
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"code\":\"AAAAAA\"}"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("ROOM_NOT_FOUND"));

		mockMvc.perform(post("/student/rooms/join")
					.header("Authorization", bearer(login))
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"code\":\"YZA789\"}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("INSTITUTION_MISMATCH"));
	}

	@Test
	void deveRejeitarCodigoAusente() throws Exception {
		AuthResponse login = login(user(Role.STUDENT, institution()));

		mockMvc.perform(post("/student/rooms/join")
					.header("Authorization", bearer(login))
					.contentType(MediaType.APPLICATION_JSON)
					.content("{}"))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}

	private Institution institution() {
		return institutionRepository.saveAndFlush(new Institution(
				"IFSC " + UUID.randomUUID(), randomCnpj(), "contato@example.com", "48999990000", true
		));
	}

	private User user(Role role, Institution institution) {
		String email = role.name().toLowerCase() + "-" + UUID.randomUUID() + "@contacerta.local";
		User user = new User(role, AccountStatus.ACTIVE, "Usuário Exemplo", email, "REG-" + UUID.randomUUID(), institution);
		user.initializePassword(passwordEncoder.encode("Senha123"), false);
		return userRepository.saveAndFlush(user);
	}

	private Room room(String name, String joinCode, User teacher, Institution institution) {
		return roomRepository.saveAndFlush(new Room(
				name, "Descrição", Grade.HIGH_SCHOOL_1, List.of("Porcentagem"), 50,
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
