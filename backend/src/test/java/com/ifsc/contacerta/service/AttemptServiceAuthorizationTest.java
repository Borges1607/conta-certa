package com.ifsc.contacerta.service;

import com.ifsc.contacerta.config.AttemptProperties;
import com.ifsc.contacerta.dto.attempt.RecordAttemptAnswerRequest;
import com.ifsc.contacerta.entity.Attempt;
import com.ifsc.contacerta.entity.User;
import com.ifsc.contacerta.exception.ApiException;
import com.ifsc.contacerta.mapper.AttemptMapper;
import com.ifsc.contacerta.model.AccountStatus;
import com.ifsc.contacerta.model.AttemptStatus;
import com.ifsc.contacerta.model.MembershipStatus;
import com.ifsc.contacerta.model.Role;
import com.ifsc.contacerta.repository.AttemptAnswerRepository;
import com.ifsc.contacerta.repository.AttemptQuestionSnapshotRepository;
import com.ifsc.contacerta.repository.AttemptRepository;
import com.ifsc.contacerta.repository.ExtraAttemptGrantRepository;
import com.ifsc.contacerta.repository.IdempotencyRecordRepository;
import com.ifsc.contacerta.repository.LessonAssignmentRepository;
import com.ifsc.contacerta.repository.QuestionRepository;
import com.ifsc.contacerta.repository.RoomMembershipRepository;
import com.ifsc.contacerta.repository.RoomStudentProgressRepository;
import com.ifsc.contacerta.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.random.RandomGenerator;

import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AttemptServiceAuthorizationTest {

	private UserRepository userRepository;
	private LessonAssignmentRepository assignmentRepository;
	private AttemptRepository attemptRepository;
	private AttemptQuestionSnapshotRepository snapshotRepository;
	private AttemptService service;

	@BeforeEach
	void setUp() {
		userRepository = mock(UserRepository.class);
		assignmentRepository = mock(LessonAssignmentRepository.class);
		attemptRepository = mock(AttemptRepository.class);
		snapshotRepository = mock(AttemptQuestionSnapshotRepository.class);
		service = new AttemptService(
				userRepository,
				assignmentRepository,
				mock(RoomMembershipRepository.class),
				mock(QuestionRepository.class),
				attemptRepository,
				snapshotRepository,
				mock(AttemptAnswerRepository.class),
				mock(ExtraAttemptGrantRepository.class),
				mock(IdempotencyRecordRepository.class),
				new AttemptProperties(Duration.ofHours(24), 100, 60_000),
				Clock.systemUTC(),
				new AttemptMapper(),
				new IdempotencyHasher(),
				new IdempotencyResponseCodec(new ObjectMapper()),
				mock(AttemptScoringService.class),
				mock(AttemptFinalizationService.class),
				mock(StudentProgressService.class),
				mock(RoomStudentProgressRepository.class),
				mock(RandomGenerator.class)
		);
	}

	@Test
	void deveOcultarAtribuicaoSemMatriculaAtivaAoIniciar() {
		User student = activeStudent();
		UUID assignmentId = UUID.randomUUID();
		when(userRepository.findById(student.getId())).thenReturn(Optional.of(student));
		when(assignmentRepository.findAccessibleByIdAndStudentId(
				assignmentId, student.getId(), MembershipStatus.ACTIVE
		)).thenReturn(Optional.empty());

		assertNotFound(
				"ASSIGNMENT_NOT_FOUND",
				() -> service.start(student.getId(), assignmentId, "attempt-key")
		);
	}

	@Test
	void deveOcultarTentativaDeOutroAluno() {
		User student = activeStudent();
		UUID attemptId = UUID.randomUUID();
		when(userRepository.findById(student.getId())).thenReturn(Optional.of(student));
		when(attemptRepository.findByIdAndStudentIdForUpdate(attemptId, student.getId()))
				.thenReturn(Optional.empty());

		assertNotFound("ATTEMPT_NOT_FOUND", () -> service.get(student.getId(), attemptId));
	}

	@Test
	void deveOcultarSnapshotDeOutraTentativa() {
		User student = activeStudent();
		UUID attemptId = UUID.randomUUID();
		UUID snapshotId = UUID.randomUUID();
		Attempt attempt = mock(Attempt.class);
		when(attempt.getId()).thenReturn(attemptId);
		when(attempt.getStatus()).thenReturn(AttemptStatus.IN_PROGRESS);
		when(userRepository.findById(student.getId())).thenReturn(Optional.of(student));
		when(attemptRepository.findByIdAndStudentIdForUpdate(attemptId, student.getId()))
				.thenReturn(Optional.of(attempt));
		when(snapshotRepository.findByIdAndAttemptIdAndAttemptStudentId(snapshotId, attemptId, student.getId()))
				.thenReturn(Optional.empty());

		assertNotFound(
				"QUESTION_SNAPSHOT_NOT_FOUND",
				() -> service.answer(student.getId(), attemptId, snapshotId, mock(RecordAttemptAnswerRequest.class))
		);
	}

	@Test
	void deveExigirAlunoAntesDeBuscarTentativa() {
		UUID teacherId = UUID.randomUUID();
		User teacher = new User(
				Role.TEACHER,
				AccountStatus.ACTIVE,
				"Professora Ana",
				"ana@example.com",
				"PROF-1",
				null
		);
		when(userRepository.findById(teacherId)).thenReturn(Optional.of(teacher));

		assertThatThrownBy(() -> service.get(teacherId, UUID.randomUUID()))
				.isInstanceOfSatisfying(ApiException.class, exception -> {
					assertThat(exception.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
					assertThat(exception.getCode()).isEqualTo("STUDENT_REQUIRED");
				});
	}

	private User activeStudent() {
		return new User(
				Role.STUDENT,
				AccountStatus.ACTIVE,
				"Aluno",
				"aluno@example.com",
				"ALU-1",
				null
		);
	}

	private void assertNotFound(String code, ThrowingCallable action) {
		assertThatThrownBy(action)
				.isInstanceOfSatisfying(ApiException.class, exception -> {
					assertThat(exception.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
					assertThat(exception.getCode()).isEqualTo(code);
				});
	}
}
