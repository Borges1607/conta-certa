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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class TeacherRoomControllerTest extends PostgresIntegrationTest {

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
	void deveExigirBearerParaRotasDeSalasDoProfessor() throws Exception {
		mockMvc.perform(get("/teacher/rooms"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
	}

	@Test
	void deveImpedirAlunoDeAcessarRotasDoProfessor() throws Exception {
		AuthResponse login = login(user(Role.STUDENT, institution()));

		mockMvc.perform(get("/teacher/rooms").header("Authorization", bearer(login)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("TEACHER_REQUIRED"));
	}

	@Test
	void deveCriarSalaERetornarLocationEDetalheDoProfessor() throws Exception {
		AuthResponse login = login(user(Role.TEACHER, institution()));

		mockMvc.perform(post("/teacher/rooms")
					.header("Authorization", bearer(login))
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{
							  "name":"2º ano A",
							  "description":"Matemática financeira",
							  "grade":"HIGH_SCHOOL_2",
							  "contentTopics":["Porcentagem"],
							  "passingScorePercent":60
							}
							"""))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern("/teacher/rooms/[0-9a-f-]{36}")))
				.andExpect(jsonPath("$.name").value("2º ano A"))
				.andExpect(jsonPath("$.joinCode").value(org.hamcrest.Matchers.matchesPattern("[A-Z0-9]{6}")))
				.andExpect(jsonPath("$.teacher.id").value(login.user().id().toString()))
				.andExpect(jsonPath("$.deletable").value(true));
	}

	@Test
	void deveListarEDetalharSomenteSalasDoProfessorAutenticado() throws Exception {
		Institution institution = institution();
		User teacher = user(Role.TEACHER, institution);
		Room ownRoom = room("Sala própria", "ABC123", teacher, institution);
		User anotherTeacher = user(Role.TEACHER, institution);
		Room foreignRoom = room("Sala alheia", "DEF456", anotherTeacher, institution);
		AuthResponse login = login(teacher);

		mockMvc.perform(get("/teacher/rooms")
					.header("Authorization", bearer(login))
					.param("search", "própria")
					.param("page", "0")
					.param("size", "10"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].id").value(ownRoom.getId().toString()))
				.andExpect(jsonPath("$.totalElements").value(1));

		mockMvc.perform(get("/teacher/rooms/{roomId}", ownRoom.getId())
					.header("Authorization", bearer(login)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(ownRoom.getId().toString()));

		mockMvc.perform(get("/teacher/rooms/{roomId}", foreignRoom.getId())
					.header("Authorization", bearer(login)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("ROOM_NOT_FOUND"));
	}

	@Test
	void deveAtualizarArquivarDuplicarERegenerarCodigoDaSalaDoProfessor() throws Exception {
		Institution institution = institution();
		User teacher = user(Role.TEACHER, institution);
		Room room = room("Sala original", "GHI789", teacher, institution);
		AuthResponse login = login(teacher);

		long version = roomRepository.findById(room.getId()).orElseThrow().getVersion();
		mockMvc.perform(patch("/teacher/rooms/{roomId}", room.getId())
					.header("Authorization", bearer(login))
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"name\":\"Sala atualizada\",\"version\":" + version + "}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Sala atualizada"));

		mockMvc.perform(post("/teacher/rooms/{roomId}/duplicate", room.getId())
					.header("Authorization", bearer(login))
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"name\":\"Sala copiada\",\"version\":" + currentVersion(room.getId()) + "}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Sala copiada"))
				.andExpect(jsonPath("$.id").value(org.hamcrest.Matchers.not(room.getId().toString())));

		mockMvc.perform(post("/teacher/rooms/{roomId}/regenerate-code", room.getId())
					.header("Authorization", bearer(login))
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"version\":" + currentVersion(room.getId()) + "}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.joinCode").value(org.hamcrest.Matchers.matchesPattern("[A-Z0-9]{6}")));

		mockMvc.perform(post("/teacher/rooms/{roomId}/archive", room.getId())
					.header("Authorization", bearer(login))
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"version\":" + currentVersion(room.getId()) + "}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.archived").value(true));

		mockMvc.perform(post("/teacher/rooms/{roomId}/archive", room.getId())
					.header("Authorization", bearer(login))
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"version\":" + version + "}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.archived").value(true));
	}

	@Test
	void deveRetornarConflitosAoAtualizarComVersaoAntigaEExcluirSalaComHistorico() throws Exception {
		Institution institution = institution();
		User teacher = user(Role.TEACHER, institution);
		Room room = room("Sala com histórico", "JKL234", teacher, institution);
		User student = user(Role.STUDENT, institution);
		membershipRepository.saveAndFlush(new RoomMembership(room, student));
		AuthResponse login = login(teacher);

		long staleVersion = roomRepository.findById(room.getId()).orElseThrow().getVersion() + 1;
		mockMvc.perform(patch("/teacher/rooms/{roomId}", room.getId())
					.header("Authorization", bearer(login))
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"name\":\"Não deve alterar\",\"version\":" + staleVersion + "}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));

		mockMvc.perform(delete("/teacher/rooms/{roomId}", room.getId())
					.header("Authorization", bearer(login))
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"version\":" + currentVersion(room.getId()) + "}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ROOM_HAS_HISTORY"));
	}

	@Test
	void deveValidarVersaoNasAcoesMutaveis() throws Exception {
		Institution institution = institution();
		User teacher = user(Role.TEACHER, institution);
		Room room = room("Sala", "RST890", teacher, institution);
		AuthResponse login = login(teacher);

		mockMvc.perform(post("/teacher/rooms/{roomId}/archive", room.getId())
					.header("Authorization", bearer(login))
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"version\":" + (currentVersion(room.getId()) + 1) + "}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
	}

	@Test
	void deveListarERemoverAlunosDaSalaDoProfessor() throws Exception {
		Institution institution = institution();
		User teacher = user(Role.TEACHER, institution);
		Room room = room("Sala com aluno", "MNO567", teacher, institution);
		User student = user(Role.STUDENT, institution);
		membershipRepository.saveAndFlush(new RoomMembership(room, student));
		AuthResponse login = login(teacher);

		mockMvc.perform(get("/teacher/rooms/{roomId}/students", room.getId())
					.header("Authorization", bearer(login)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].studentId").value(student.getId().toString()));

		mockMvc.perform(delete("/teacher/rooms/{roomId}/students/{studentId}", room.getId(), student.getId())
					.header("Authorization", bearer(login)))
				.andExpect(status().isNoContent());
	}

	@Test
	void deveExigirProfessorEValidarPaginacaoAoGerenciarAlunos() throws Exception {
		Institution institution = institution();
		User teacher = user(Role.TEACHER, institution);
		User student = user(Role.STUDENT, institution);
		Room room = room("Sala", "UVW123", teacher, institution);
		AuthResponse studentLogin = login(student);

		mockMvc.perform(get("/teacher/rooms/{roomId}/students", room.getId())
					.header("Authorization", bearer(studentLogin)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("TEACHER_REQUIRED"));

		mockMvc.perform(delete("/teacher/rooms/{roomId}/students/{studentId}", room.getId(), student.getId())
					.header("Authorization", bearer(studentLogin)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("TEACHER_REQUIRED"));

		AuthResponse teacherLogin = login(teacher);
		mockMvc.perform(get("/teacher/rooms/{roomId}/students", room.getId())
					.header("Authorization", bearer(teacherLogin))
					.param("page", "-1"))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("INVALID_PAGE"));
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

	private long currentVersion(UUID roomId) {
		return roomRepository.findById(roomId).orElseThrow().getVersion();
	}

	private String randomCnpj() {
		return "%014d".formatted(Math.abs(UUID.randomUUID().getMostSignificantBits()) % 100000000000000L);
	}
}
