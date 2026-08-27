package com.ifsc.contacerta.controller;

import com.ifsc.contacerta.dto.auth.AuthResponse;
import com.ifsc.contacerta.dto.auth.LoginRequest;
import com.ifsc.contacerta.entity.Institution;
import com.ifsc.contacerta.entity.Lesson;
import com.ifsc.contacerta.entity.LessonAssignment;
import com.ifsc.contacerta.entity.Question;
import com.ifsc.contacerta.entity.Room;
import com.ifsc.contacerta.entity.User;
import com.ifsc.contacerta.model.AccountStatus;
import com.ifsc.contacerta.model.ContentStatus;
import com.ifsc.contacerta.model.Grade;
import com.ifsc.contacerta.model.QuestionType;
import com.ifsc.contacerta.model.Role;
import com.ifsc.contacerta.repository.InstitutionRepository;
import com.ifsc.contacerta.repository.LessonAssignmentRepository;
import com.ifsc.contacerta.repository.LessonRepository;
import com.ifsc.contacerta.repository.QuestionRepository;
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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class TeacherLessonAssignmentControllerTest extends PostgresIntegrationTest {

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
	private LessonRepository lessonRepository;
	@Autowired
	private QuestionRepository questionRepository;
	@Autowired
	private LessonAssignmentRepository assignmentRepository;
	@Autowired
	private PasswordEncoder passwordEncoder;

	private final JoinCodeHasher joinCodeHasher = new JoinCodeHasher();

	@Test
	void deveExigirBearerEProfessor() throws Exception {
		mockMvc.perform(get("/teacher/rooms/{roomId}/lesson-assignments", UUID.randomUUID()))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));

		Institution institution = institution();
		User student = user(Role.STUDENT, institution);
		AuthResponse login = login(student);
		mockMvc.perform(get("/teacher/rooms/{roomId}/lesson-assignments", UUID.randomUUID())
					.header("Authorization", bearer(login)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("TEACHER_REQUIRED"));
	}

	@Test
	void deveCriarEListarAtribuicaoComPadroes() throws Exception {
		Fixture fixture = fixture();

		mockMvc.perform(post("/teacher/rooms/{roomId}/lesson-assignments", fixture.room().getId())
					.header("Authorization", bearer(fixture.login()))
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"lessonId":"%s","status":"DRAFT"}
							""".formatted(fixture.lesson().getId())))
				.andExpect(status().isCreated())
				.andExpect(header().string(
						"Location",
						matchesPattern("/teacher/rooms/[0-9a-f-]{36}/lesson-assignments/[0-9a-f-]{36}")
				))
				.andExpect(jsonPath("$.lessonId").value(fixture.lesson().getId().toString()))
				.andExpect(jsonPath("$.position").value(1))
				.andExpect(jsonPath("$.timeLimitMinutes").value(30))
				.andExpect(jsonPath("$.maxAttempts").value(3));

		mockMvc.perform(get("/teacher/rooms/{roomId}/lesson-assignments", fixture.room().getId())
					.header("Authorization", bearer(fixture.login())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].lessonTitle").value(fixture.lesson().getTitle()))
				.andExpect(jsonPath("$[0].activeQuestionCount").value(1));
	}

	@Test
	void deveAtualizarEPublicarAtribuicao() throws Exception {
		Fixture fixture = fixture();
		LessonAssignment assignment = assignmentRepository.saveAndFlush(new LessonAssignment(
				fixture.room(), fixture.lesson(), 1, null, null, 30, 3, null, true, true
		));

		mockMvc.perform(patch(
					"/teacher/rooms/{roomId}/lesson-assignments/{assignmentId}",
					fixture.room().getId(),
					assignment.getId()
			)
					.header("Authorization", bearer(fixture.login()))
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"status":"PUBLISHED","timeLimitMinutes":null,"version":%d}
							""".formatted(assignment.getVersion())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("PUBLISHED"))
				.andExpect(jsonPath("$.timeLimitMinutes").doesNotExist());
	}

	@Test
	void deveReordenarTrilhaCompleta() throws Exception {
		Fixture fixture = fixture();
		Lesson secondLesson = publishedLesson("Segunda lição", fixture.teacher());
		LessonAssignment first = assignmentRepository.saveAndFlush(new LessonAssignment(
				fixture.room(), fixture.lesson(), 1, null, null, 30, 3, null, true, true
		));
		LessonAssignment second = assignmentRepository.saveAndFlush(new LessonAssignment(
				fixture.room(), secondLesson, 2, null, null, 30, 3, null, true, true
		));

		mockMvc.perform(put("/teacher/rooms/{roomId}/lesson-assignments/order", fixture.room().getId())
					.header("Authorization", bearer(fixture.login()))
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"assignments":[
							  {"assignmentId":"%s","version":%d},
							  {"assignmentId":"%s","version":%d}
							]}
							""".formatted(
							second.getId(), second.getVersion(), first.getId(), first.getVersion()
						)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value(second.getId().toString()))
				.andExpect(jsonPath("$[0].position").value(1))
				.andExpect(jsonPath("$[1].id").value(first.getId().toString()))
				.andExpect(jsonPath("$[1].position").value(2));
	}

	@Test
	void deveRemoverRascunhoEFecharLacuna() throws Exception {
		Fixture fixture = fixture();
		Lesson secondLesson = publishedLesson("Segunda lição", fixture.teacher());
		LessonAssignment removed = assignmentRepository.saveAndFlush(new LessonAssignment(
				fixture.room(), fixture.lesson(), 1, null, null, 30, 3, null, true, true
		));
		LessonAssignment remaining = assignmentRepository.saveAndFlush(new LessonAssignment(
				fixture.room(), secondLesson, 2, null, null, 30, 3, null, true, true
		));

		mockMvc.perform(delete(
					"/teacher/rooms/{roomId}/lesson-assignments/{assignmentId}",
					fixture.room().getId(),
					removed.getId()
			)
					.header("Authorization", bearer(fixture.login()))
					.param("version", Long.toString(removed.getVersion())))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/teacher/rooms/{roomId}/lesson-assignments", fixture.room().getId())
					.header("Authorization", bearer(fixture.login())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].id").value(remaining.getId().toString()))
				.andExpect(jsonPath("$[0].position").value(1));
	}

	@Test
	void deveRetornarErrosDeValidacaoPropriedadeEConflito() throws Exception {
		Fixture fixture = fixture();

		mockMvc.perform(post("/teacher/rooms/{roomId}/lesson-assignments", fixture.room().getId())
					.header("Authorization", bearer(fixture.login()))
					.contentType(MediaType.APPLICATION_JSON)
					.content("{}"))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

		mockMvc.perform(post("/teacher/rooms/{roomId}/lesson-assignments", UUID.randomUUID())
					.header("Authorization", bearer(fixture.login()))
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"lessonId":"%s"}
							""".formatted(fixture.lesson().getId())))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("ROOM_NOT_FOUND"));

		assignmentRepository.saveAndFlush(new LessonAssignment(
				fixture.room(), fixture.lesson(), 1, null, null, 30, 3, null, true, true
		));
		mockMvc.perform(post("/teacher/rooms/{roomId}/lesson-assignments", fixture.room().getId())
					.header("Authorization", bearer(fixture.login()))
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"lessonId":"%s"}
							""".formatted(fixture.lesson().getId())))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("LESSON_ALREADY_ASSIGNED"));
	}

	@Test
	void deveImpedirRemocaoDeAtribuicaoDisponivel() throws Exception {
		Fixture fixture = fixture();
		LessonAssignment assignment = new LessonAssignment(
				fixture.room(), fixture.lesson(), 1, null, Instant.parse("2026-09-30T12:00:00Z"),
				30, 3, null, true, true
		);
		assignment.publish();
		assignmentRepository.saveAndFlush(assignment);

		mockMvc.perform(delete(
					"/teacher/rooms/{roomId}/lesson-assignments/{assignmentId}",
					fixture.room().getId(),
					assignment.getId()
			)
					.header("Authorization", bearer(fixture.login()))
					.param("version", Long.toString(assignment.getVersion())))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ASSIGNMENT_ALREADY_IN_USE"));
	}

	private Fixture fixture() {
		Institution institution = institution();
		User teacher = user(Role.TEACHER, institution);
		Room room = room(teacher, institution);
		Lesson lesson = publishedLesson("Juros compostos", teacher);
		return new Fixture(teacher, room, lesson, login(teacher));
	}

	private Institution institution() {
		return institutionRepository.saveAndFlush(new Institution(
				"IFSC " + UUID.randomUUID(), randomCnpj(), "contato@example.com", "48999990000", true
		));
	}

	private User user(Role role, Institution institution) {
		String email = role.name().toLowerCase() + "-" + UUID.randomUUID() + "@contacerta.local";
		User user = new User(
				role, AccountStatus.ACTIVE, "Usuário Exemplo", email, "REG-" + UUID.randomUUID(), institution
		);
		user.initializePassword(passwordEncoder.encode("Senha123"), false);
		return userRepository.saveAndFlush(user);
	}

	private Room room(User teacher, Institution institution) {
		String code = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
		return roomRepository.saveAndFlush(new Room(
				"Sala", "Descrição", Grade.HIGH_SCHOOL_1, List.of("Porcentagem"), 50,
				code, joinCodeHasher.hash(code), teacher, institution
		));
	}

	private Lesson publishedLesson(String title, User teacher) {
		Lesson lesson = new Lesson(title, null, "# Teoria", teacher);
		lesson.publish();
		lessonRepository.saveAndFlush(lesson);
		Question question = Question.choice(
				lesson, QuestionType.TRUE_FALSE, "Verdadeiro ou falso?", null, List.of()
		);
		question.configureBoolean(true);
		questionRepository.saveAndFlush(question);
		return lesson;
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

	private record Fixture(User teacher, Room room, Lesson lesson, AuthResponse login) {
	}
}
