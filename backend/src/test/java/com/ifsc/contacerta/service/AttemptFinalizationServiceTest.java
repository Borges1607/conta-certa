package com.ifsc.contacerta.service;

import com.ifsc.contacerta.entity.Attempt;
import com.ifsc.contacerta.entity.AttemptAnswer;
import com.ifsc.contacerta.entity.Institution;
import com.ifsc.contacerta.entity.Lesson;
import com.ifsc.contacerta.entity.LessonAssignment;
import com.ifsc.contacerta.entity.Question;
import com.ifsc.contacerta.entity.Room;
import com.ifsc.contacerta.entity.RoomMembership;
import com.ifsc.contacerta.entity.RoomStudentProgress;
import com.ifsc.contacerta.entity.User;
import com.ifsc.contacerta.model.AccountStatus;
import com.ifsc.contacerta.model.AttemptStatus;
import com.ifsc.contacerta.model.Grade;
import com.ifsc.contacerta.model.QuestionType;
import com.ifsc.contacerta.model.Role;
import com.ifsc.contacerta.repository.AttemptAnswerRepository;
import com.ifsc.contacerta.repository.AttemptRepository;
import com.ifsc.contacerta.repository.RoomMembershipRepository;
import com.ifsc.contacerta.repository.RoomStudentProgressRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AttemptFinalizationServiceTest {

	@Test
	void deveFinalizarPontuarECreditarSomenteODeltaNoProgresso() {
		Fixture fixture = fixture();
		AttemptRepository attemptRepository = mock(AttemptRepository.class);
		AttemptAnswerRepository answerRepository = mock(AttemptAnswerRepository.class);
		RoomMembershipRepository membershipRepository = mock(RoomMembershipRepository.class);
		RoomStudentProgressRepository progressRepository = mock(RoomStudentProgressRepository.class);
		AttemptFinalizationService service = new AttemptFinalizationService(
				attemptRepository, answerRepository, membershipRepository, progressRepository
		);
		Instant finalizedAt = Instant.parse("2026-08-28T12:30:00Z");
		AttemptAnswer correctAnswer = AttemptAnswer.booleanAnswer(
				fixture.attempt().getSnapshots().getFirst(), true, true, finalizedAt.minusSeconds(10)
		);
		RoomMembership membership = new RoomMembership(fixture.room(), fixture.student());
		when(membershipRepository.findForUpdateByRoomIdAndStudentId(fixture.room().getId(), fixture.student().getId()))
				.thenReturn(Optional.of(membership));
		when(answerRepository.findByQuestionSnapshotAttemptId(fixture.attempt().getId()))
				.thenReturn(List.of(correctAnswer));
		when(progressRepository.findForUpdateByRoomIdAndStudentId(fixture.room().getId(), fixture.student().getId()))
				.thenReturn(Optional.empty());
		when(progressRepository.save(any(RoomStudentProgress.class))).thenAnswer(invocation -> invocation.getArgument(0));

		service.finalizeAttempt(fixture.attempt(), AttemptStatus.SUBMITTED, finalizedAt);

		assertThat(fixture.attempt().getStatus()).isEqualTo(AttemptStatus.SUBMITTED);
		assertThat(fixture.attempt().getCorrectAnswers()).isEqualTo(1);
		assertThat(fixture.attempt().getTotalQuestions()).isEqualTo(2);
		assertThat(fixture.attempt().getScorePercent()).isEqualTo(50);
		assertThat(fixture.attempt().getPassed()).isFalse();
		assertThat(fixture.attempt().getStars()).isEqualTo(1);
		assertThat(fixture.attempt().getXpCredited()).isEqualTo(10);
	}

	private Fixture fixture() {
		Institution institution = new Institution("Instituto", "11222333000181", "contato@example.com", "48999990000", true);
		User teacher = new User(Role.TEACHER, AccountStatus.ACTIVE, "Professora", "prof@example.com", "P-1", institution);
		User student = new User(Role.STUDENT, AccountStatus.ACTIVE, "Aluno", "aluno@example.com", "A-1", institution);
		Room room = new Room("Sala", null, Grade.HIGH_SCHOOL_1, List.of("Frações"), 60, "ABC234", "hash", teacher, institution);
		Lesson lesson = new Lesson("Frações", null, "# Teoria", teacher);
		LessonAssignment assignment = new LessonAssignment(room, lesson, 1, null, null, 30, 3, 2, false, false);
		Attempt attempt = new Attempt(assignment, student, 1, Instant.parse("2026-08-28T12:00:00Z"), null);
		Question first = Question.choice(lesson, QuestionType.TRUE_FALSE, "Primeira?", null, List.of());
		first.configureBoolean(true);
		Question second = Question.choice(lesson, QuestionType.TRUE_FALSE, "Segunda?", null, List.of());
		second.configureBoolean(false);
		attempt.addSnapshot(first, 1, List.of());
		attempt.addSnapshot(second, 2, List.of());
		return new Fixture(student, room, attempt);
	}

	private record Fixture(User student, Room room, Attempt attempt) {}
}
