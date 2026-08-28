package com.ifsc.contacerta.service;

import com.ifsc.contacerta.config.AttemptProperties;
import com.ifsc.contacerta.entity.User;
import com.ifsc.contacerta.exception.ApiException;
import com.ifsc.contacerta.mapper.AttemptMapper;
import com.ifsc.contacerta.model.AccountStatus;
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
	private AttemptService service;

	@BeforeEach
	void setUp() {
		userRepository = mock(UserRepository.class);
		service = new AttemptService(
				userRepository,
				mock(LessonAssignmentRepository.class),
				mock(RoomMembershipRepository.class),
				mock(QuestionRepository.class),
				mock(AttemptRepository.class),
				mock(AttemptQuestionSnapshotRepository.class),
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
}
