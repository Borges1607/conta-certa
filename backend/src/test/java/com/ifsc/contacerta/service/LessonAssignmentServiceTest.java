package com.ifsc.contacerta.service;

import com.ifsc.contacerta.dto.assignment.CreateLessonAssignmentRequest;
import com.ifsc.contacerta.dto.assignment.LessonAssignmentResponse;
import com.ifsc.contacerta.dto.assignment.UpdateLessonAssignmentRequest;
import com.ifsc.contacerta.entity.Institution;
import com.ifsc.contacerta.entity.Lesson;
import com.ifsc.contacerta.entity.LessonAssignment;
import com.ifsc.contacerta.entity.Room;
import com.ifsc.contacerta.entity.User;
import com.ifsc.contacerta.exception.ApiException;
import com.ifsc.contacerta.model.AccountStatus;
import com.ifsc.contacerta.model.ContentStatus;
import com.ifsc.contacerta.model.Grade;
import com.ifsc.contacerta.model.Role;
import com.ifsc.contacerta.repository.LessonAssignmentRepository;
import com.ifsc.contacerta.repository.LessonRepository;
import com.ifsc.contacerta.repository.QuestionRepository;
import com.ifsc.contacerta.repository.RoomRepository;
import com.ifsc.contacerta.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.IntNode;
import tools.jackson.databind.node.NullNode;
import tools.jackson.databind.node.StringNode;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LessonAssignmentServiceTest {

	private UserRepository userRepository;
	private RoomRepository roomRepository;
	private LessonRepository lessonRepository;
	private LessonAssignmentRepository assignmentRepository;
	private QuestionRepository questionRepository;
	private LessonAssignmentService service;
	private User teacher;
	private Room room;
	private Lesson lesson;

	@BeforeEach
	void setUp() {
		userRepository = mock(UserRepository.class);
		roomRepository = mock(RoomRepository.class);
		lessonRepository = mock(LessonRepository.class);
		assignmentRepository = mock(LessonAssignmentRepository.class);
		questionRepository = mock(QuestionRepository.class);
		Institution institution = new Institution(
				"Instituto Exemplo", "11222333000181", "contato@example.com", "48999990000", true
		);
		teacher = new User(
				Role.TEACHER, AccountStatus.ACTIVE, "Professora Ana", "ana@example.com", "PROF-1", institution
		);
		room = new Room(
				"Sala A", null, Grade.HIGH_SCHOOL_1, List.of("Porcentagem"), 50,
				"ABC234", "hash-a", teacher, institution
		);
		lesson = new Lesson("Juros compostos", null, "# Teoria", teacher);
		when(userRepository.findById(teacher.getId())).thenReturn(Optional.of(teacher));
		when(roomRepository.findByIdAndTeacherId(room.getId(), teacher.getId())).thenReturn(Optional.of(room));
		when(lessonRepository.findByIdAndTeacherId(lesson.getId(), teacher.getId())).thenReturn(Optional.of(lesson));
		when(assignmentRepository.findByRoomIdForUpdate(room.getId())).thenReturn(new ArrayList<>());
		when(assignmentRepository.findByRoomIdAndRoomTeacherIdOrderByPositionAsc(room.getId(), teacher.getId()))
				.thenReturn(List.of());
		when(assignmentRepository.save(any(LessonAssignment.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(questionRepository.countByLessonIdAndActiveTrue(lesson.getId())).thenReturn(5L);
		service = new LessonAssignmentService(
				userRepository,
				roomRepository,
				lessonRepository,
				assignmentRepository,
				questionRepository,
				Clock.fixed(Instant.parse("2026-08-27T12:00:00Z"), ZoneOffset.UTC)
		);
	}

	@Test
	void deveAplicarPadroesQuandoLimitesForemOmitidos() {
		LessonAssignmentResponse response = service.create(
				teacher.getId(), room.getId(), request(lesson, null, null, null, null)
		);

		assertThat(response.position()).isEqualTo(1);
		assertThat(response.status()).isEqualTo(ContentStatus.DRAFT);
		assertThat(response.timeLimitMinutes()).isEqualTo(30);
		assertThat(response.maxAttempts()).isEqualTo(3);
		assertThat(response.questionCount()).isNull();
		assertThat(response.shuffleQuestions()).isTrue();
		assertThat(response.shuffleOptions()).isTrue();
	}

	@Test
	void deveAceitarLimitesNulosExplicitos() {
		LessonAssignmentResponse response = service.create(
				teacher.getId(),
				room.getId(),
				request(lesson, null, NullNode.getInstance(), NullNode.getInstance(), NullNode.getInstance())
		);

		assertThat(response.timeLimitMinutes()).isNull();
		assertThat(response.maxAttempts()).isNull();
		assertThat(response.questionCount()).isNull();
	}

	@Test
	void deveRejeitarPublicacaoComQuestoesInsuficientes() {
		lesson.publish();
		when(questionRepository.countByLessonIdAndActiveTrue(lesson.getId())).thenReturn(2L);
		CreateLessonAssignmentRequest request = new CreateLessonAssignmentRequest(
				lesson.getId(), null, ContentStatus.PUBLISHED, null, null,
				null, null, IntNode.valueOf(3), true, true
		);

		assertApiError(
				HttpStatus.UNPROCESSABLE_CONTENT,
				"INSUFFICIENT_ACTIVE_QUESTIONS",
				() -> service.create(teacher.getId(), room.getId(), request)
		);
	}

	@Test
	void deveExigirProfessorAtivo() {
		User inactive = new User(
				Role.TEACHER, AccountStatus.INACTIVE, "Professor Inativo", "inativo@example.com", "PROF-2",
				teacher.getInstitution()
		);
		when(userRepository.findById(inactive.getId())).thenReturn(Optional.of(inactive));

		assertApiError(
				HttpStatus.FORBIDDEN,
				"ACCOUNT_INACTIVE",
				() -> service.create(inactive.getId(), room.getId(), request(lesson, null, null, null, null))
		);
	}

	@Test
	void deveOcultarSalaOuLicaoDeOutroProfessor() {
		when(roomRepository.findByIdAndTeacherId(room.getId(), teacher.getId())).thenReturn(Optional.empty());

		assertApiError(
				HttpStatus.NOT_FOUND,
				"ROOM_NOT_FOUND",
				() -> service.create(teacher.getId(), room.getId(), request(lesson, null, null, null, null))
		);

		when(roomRepository.findByIdAndTeacherId(room.getId(), teacher.getId())).thenReturn(Optional.of(room));
		when(lessonRepository.findByIdAndTeacherId(lesson.getId(), teacher.getId())).thenReturn(Optional.empty());
		assertApiError(
				HttpStatus.NOT_FOUND,
				"LESSON_NOT_FOUND",
				() -> service.create(teacher.getId(), room.getId(), request(lesson, null, null, null, null))
		);
	}

	@Test
	void deveImpedirAtribuicaoEmSalaArquivada() {
		room.archive();

		assertApiError(
				HttpStatus.UNPROCESSABLE_CONTENT,
				"ROOM_ARCHIVED",
				() -> service.create(teacher.getId(), room.getId(), request(lesson, null, null, null, null))
		);
	}

	@Test
	void deveExigirLicaoPublicadaParaAtribuicaoPublicada() {
		CreateLessonAssignmentRequest request = new CreateLessonAssignmentRequest(
				lesson.getId(), null, ContentStatus.PUBLISHED, null, null,
				null, null, null, null, null
		);

		assertApiError(
				HttpStatus.UNPROCESSABLE_CONTENT,
				"LESSON_NOT_PUBLISHED",
				() -> service.create(teacher.getId(), room.getId(), request)
		);
	}

	@Test
	void deveImpedirLicaoDuplicadaNaSala() {
		when(assignmentRepository.existsByRoomIdAndLessonId(room.getId(), lesson.getId())).thenReturn(true);

		assertApiError(
				HttpStatus.CONFLICT,
				"LESSON_ALREADY_ASSIGNED",
				() -> service.create(teacher.getId(), room.getId(), request(lesson, null, null, null, null))
		);
	}

	@Test
	void deveAbrirEspacoAoInserirNaPrimeiraPosicao() {
		Lesson firstLesson = new Lesson("Primeira", null, "# Primeira", teacher);
		Lesson secondLesson = new Lesson("Segunda", null, "# Segunda", teacher);
		LessonAssignment first = assignment(firstLesson, 1);
		LessonAssignment second = assignment(secondLesson, 2);
		List<LessonAssignment> assignments = new ArrayList<>(List.of(first, second));
		when(assignmentRepository.findByRoomIdForUpdate(room.getId())).thenReturn(assignments);

		LessonAssignmentResponse response = service.create(
				teacher.getId(), room.getId(), request(lesson, 1, null, null, null)
		);

		assertThat(response.position()).isEqualTo(1);
		assertThat(first.getPosition()).isEqualTo(2);
		assertThat(second.getPosition()).isEqualTo(3);
	}

	@Test
	void deveListarSomenteAtribuicoesDaSalaDoProfessor() {
		LessonAssignment second = assignment(new Lesson("Segunda", null, "# Segunda", teacher), 2);
		LessonAssignment first = assignment(lesson, 1);
		when(assignmentRepository.findByRoomIdAndRoomTeacherIdOrderByPositionAsc(room.getId(), teacher.getId()))
				.thenReturn(List.of(first, second));

		assertThat(service.list(teacher.getId(), room.getId()))
				.extracting(LessonAssignmentResponse::lessonTitle, LessonAssignmentResponse::position)
				.containsExactly(
						tuple("Juros compostos", 1),
						tuple("Segunda", 2)
				);
	}

	@Test
	void deveLimparLimiteComNullExplicito() {
		LessonAssignment assignment = assignment(lesson, 1);
		stubOwnedAssignment(assignment);
		UpdateLessonAssignmentRequest request = new UpdateLessonAssignmentRequest(
				null, null, null, NullNode.getInstance(), null, null, null, null, assignment.getVersion()
		);

		LessonAssignmentResponse response = service.update(
				teacher.getId(), room.getId(), assignment.getId(), request
		);

		assertThat(response.timeLimitMinutes()).isNull();
		assertThat(response.maxAttempts()).isEqualTo(3);
	}

	@Test
	void devePreservarCamposOmitidos() {
		LessonAssignment assignment = assignment(lesson, 1);
		stubOwnedAssignment(assignment);
		UpdateLessonAssignmentRequest request = new UpdateLessonAssignmentRequest(
				null, null, null, null, null, null, null, null, assignment.getVersion()
		);

		LessonAssignmentResponse response = service.update(
				teacher.getId(), room.getId(), assignment.getId(), request
		);

		assertThat(response.status()).isEqualTo(ContentStatus.DRAFT);
		assertThat(response.timeLimitMinutes()).isEqualTo(30);
		assertThat(response.maxAttempts()).isEqualTo(3);
		assertThat(response.shuffleQuestions()).isTrue();
		assertThat(response.shuffleOptions()).isTrue();
	}

	@Test
	void deveRejeitarIntervaloDeDatasInvalido() {
		LessonAssignment assignment = assignment(lesson, 1);
		stubOwnedAssignment(assignment);
		UpdateLessonAssignmentRequest request = new UpdateLessonAssignmentRequest(
				null,
				StringNode.valueOf("2026-09-10T12:00:00Z"),
				StringNode.valueOf("2026-09-01T12:00:00Z"),
				null, null, null, null, null, assignment.getVersion()
		);

		assertApiError(
				HttpStatus.UNPROCESSABLE_CONTENT,
				"INVALID_ASSIGNMENT_DATES",
				() -> service.update(teacher.getId(), room.getId(), assignment.getId(), request)
		);
	}

	@Test
	void deveRejeitarLimitesNaoPositivos() {
		LessonAssignment assignment = assignment(lesson, 1);
		stubOwnedAssignment(assignment);
		UpdateLessonAssignmentRequest request = new UpdateLessonAssignmentRequest(
				null, null, null, IntNode.valueOf(0), null, null, null, null, assignment.getVersion()
		);

		assertApiError(
				HttpStatus.UNPROCESSABLE_CONTENT,
				"INVALID_ASSIGNMENT_LIMIT",
				() -> service.update(teacher.getId(), room.getId(), assignment.getId(), request)
		);
	}

	@Test
	void deveRevalidarQuestoesAoPublicar() {
		lesson.publish();
		LessonAssignment assignment = assignment(lesson, 1);
		stubOwnedAssignment(assignment);
		when(questionRepository.countByLessonIdAndActiveTrue(lesson.getId())).thenReturn(0L);
		UpdateLessonAssignmentRequest request = new UpdateLessonAssignmentRequest(
				ContentStatus.PUBLISHED, null, null, null, null, null, null, null, assignment.getVersion()
		);

		assertApiError(
				HttpStatus.UNPROCESSABLE_CONTENT,
				"INSUFFICIENT_ACTIVE_QUESTIONS",
				() -> service.update(teacher.getId(), room.getId(), assignment.getId(), request)
		);
	}

	@Test
	void deveImpedirAlteracaoArquivada() {
		LessonAssignment assignment = assignment(lesson, 1);
		assignment.archive();
		stubOwnedAssignment(assignment);
		UpdateLessonAssignmentRequest request = new UpdateLessonAssignmentRequest(
				null, null, null, null, null, null, false, null, assignment.getVersion()
		);

		assertApiError(
				HttpStatus.UNPROCESSABLE_CONTENT,
				"ASSIGNMENT_ARCHIVED",
				() -> service.update(teacher.getId(), room.getId(), assignment.getId(), request)
		);
	}

	@Test
	void deveRemoverRascunhoEFecharLacuna() {
		LessonAssignment removed = assignment(lesson, 1);
		LessonAssignment remaining = assignment(new Lesson("Segunda", null, "# Segunda", teacher), 2);
		stubOwnedAssignment(removed);
		when(assignmentRepository.findByRoomIdForUpdate(room.getId()))
				.thenReturn(new ArrayList<>(List.of(removed, remaining)));

		service.delete(teacher.getId(), room.getId(), removed.getId(), removed.getVersion());

		assertThat(remaining.getPosition()).isEqualTo(1);
	}

	@Test
	void deveRemoverPublicacaoFutura() {
		lesson.publish();
		LessonAssignment assignment = assignment(lesson, 1);
		assignment.configure(
				ContentStatus.PUBLISHED,
				Instant.parse("2026-08-28T12:00:00Z"),
				null,
				30,
				3,
				null,
				true,
				true
		);
		stubOwnedAssignment(assignment);
		when(assignmentRepository.findByRoomIdForUpdate(room.getId()))
				.thenReturn(new ArrayList<>(List.of(assignment)));

		service.delete(teacher.getId(), room.getId(), assignment.getId(), assignment.getVersion());
	}

	@Test
	void deveImpedirRemocaoDeAtribuicaoJaDisponivel() {
		lesson.publish();
		LessonAssignment assignment = assignment(lesson, 1);
		assignment.publish();
		stubOwnedAssignment(assignment);

		assertApiError(
				HttpStatus.CONFLICT,
				"ASSIGNMENT_ALREADY_IN_USE",
				() -> service.delete(teacher.getId(), room.getId(), assignment.getId(), assignment.getVersion())
		);
	}

	@Test
	void deveRejeitarVersaoDesatualizada() {
		LessonAssignment assignment = assignment(lesson, 1);
		stubOwnedAssignment(assignment);
		UpdateLessonAssignmentRequest request = new UpdateLessonAssignmentRequest(
				null, null, null, null, null, null, null, null, assignment.getVersion() + 1
		);

		assertApiError(
				HttpStatus.CONFLICT,
				"VERSION_CONFLICT",
				() -> service.update(teacher.getId(), room.getId(), assignment.getId(), request)
		);
	}

	@Test
	void deveOcultarAtribuicaoDeOutroProfessor() {
		LessonAssignment assignment = assignment(lesson, 1);
		when(assignmentRepository.findByIdAndRoomIdAndRoomTeacherId(
				assignment.getId(), room.getId(), teacher.getId()
		)).thenReturn(Optional.empty());
		UpdateLessonAssignmentRequest request = new UpdateLessonAssignmentRequest(
				null, null, null, null, null, null, null, null, assignment.getVersion()
		);

		assertApiError(
				HttpStatus.NOT_FOUND,
				"ASSIGNMENT_NOT_FOUND",
				() -> service.update(teacher.getId(), room.getId(), assignment.getId(), request)
		);
	}

	private CreateLessonAssignmentRequest request(
			Lesson requestedLesson,
			Integer position,
			JsonNode timeLimit,
			JsonNode maxAttempts,
			JsonNode questionCount
	) {
		return new CreateLessonAssignmentRequest(
				requestedLesson.getId(), position, null, null, null,
				timeLimit, maxAttempts, questionCount, null, null
		);
	}

	private LessonAssignment assignment(Lesson assignedLesson, int position) {
		return new LessonAssignment(room, assignedLesson, position, null, null, 30, 3, null, true, true);
	}

	private void stubOwnedAssignment(LessonAssignment assignment) {
		when(assignmentRepository.findByIdAndRoomIdAndRoomTeacherId(
				assignment.getId(), room.getId(), teacher.getId()
		)).thenReturn(Optional.of(assignment));
	}

	private void assertApiError(HttpStatus status, String code, Runnable operation) {
		assertThatThrownBy(operation::run)
				.isInstanceOfSatisfying(ApiException.class, exception -> {
					assertThat(exception.getStatus()).isEqualTo(status);
					assertThat(exception.getCode()).isEqualTo(code);
				});
	}
}
