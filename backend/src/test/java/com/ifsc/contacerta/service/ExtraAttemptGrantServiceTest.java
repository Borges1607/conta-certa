package com.ifsc.contacerta.service;

import com.ifsc.contacerta.dto.extraattempt.CreateExtraAttemptGrantRequest;
import com.ifsc.contacerta.entity.Institution;
import com.ifsc.contacerta.entity.Lesson;
import com.ifsc.contacerta.entity.LessonAssignment;
import com.ifsc.contacerta.entity.Room;
import com.ifsc.contacerta.entity.RoomMembership;
import com.ifsc.contacerta.entity.User;
import com.ifsc.contacerta.exception.ApiException;
import com.ifsc.contacerta.model.AccountStatus;
import com.ifsc.contacerta.model.Grade;
import com.ifsc.contacerta.model.Role;
import com.ifsc.contacerta.repository.AttemptRepository;
import com.ifsc.contacerta.repository.ExtraAttemptGrantRepository;
import com.ifsc.contacerta.repository.LessonAssignmentRepository;
import com.ifsc.contacerta.repository.RoomMembershipRepository;
import com.ifsc.contacerta.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExtraAttemptGrantServiceTest {

	private UserRepository userRepository;
	private LessonAssignmentRepository assignmentRepository;
	private RoomMembershipRepository membershipRepository;
	private ExtraAttemptGrantRepository grantRepository;
	private AttemptRepository attemptRepository;
	private ExtraAttemptGrantService service;
	private User teacher;
	private User student;
	private LessonAssignment assignment;

	@BeforeEach
	void setUp() {
		userRepository = mock(UserRepository.class);
		assignmentRepository = mock(LessonAssignmentRepository.class);
		membershipRepository = mock(RoomMembershipRepository.class);
		grantRepository = mock(ExtraAttemptGrantRepository.class);
		attemptRepository = mock(AttemptRepository.class);

		Institution institution = new Institution(
				"Instituto Exemplo", "11222333000181", "contato@example.com", "48999990000", true
		);
		teacher = new User(
				Role.TEACHER, AccountStatus.ACTIVE, "Professora Ana", "ana@example.com", "PROF-1", institution
		);
		student = new User(
				Role.STUDENT, AccountStatus.ACTIVE, "Aluno Bruno", "bruno@example.com", "ALUNO-1", institution
		);
		Room room = new Room(
				"Sala A", null, Grade.HIGH_SCHOOL_1, List.of("Porcentagem"), 50,
				"ABC234", "hash-a", teacher, institution
		);
		Lesson lesson = new Lesson("Juros compostos", null, "# Teoria", teacher);
		assignment = new LessonAssignment(room, lesson, 1, null, null, 30, 3, null, true, true);

		when(userRepository.findById(teacher.getId())).thenReturn(Optional.of(teacher));
		when(userRepository.findById(student.getId())).thenReturn(Optional.of(student));
		when(assignmentRepository.findById(assignment.getId())).thenReturn(Optional.of(assignment));
		when(membershipRepository.findForUpdateByRoomIdAndStudentId(room.getId(), student.getId()))
				.thenReturn(Optional.of(new RoomMembership(room, student)));

		service = new ExtraAttemptGrantService(
				userRepository,
				assignmentRepository,
				membershipRepository,
				grantRepository,
				attemptRepository,
				Clock.fixed(Instant.parse("2026-08-28T12:00:00Z"), ZoneOffset.UTC)
		);
	}

	@Test
	void deveExigirProfessorAtivo() {
		User inactiveTeacher = new User(
				Role.TEACHER, AccountStatus.INACTIVE, "Professora Inativa", "inativa@example.com", "PROF-2",
				teacher.getInstitution()
		);
		when(userRepository.findById(inactiveTeacher.getId())).thenReturn(Optional.of(inactiveTeacher));

		assertApiError(
				HttpStatus.FORBIDDEN,
				"ACCOUNT_INACTIVE",
				() -> service.grant(
						inactiveTeacher.getId(), assignment.getId(), student.getId(), new CreateExtraAttemptGrantRequest(1)
				)
		);
	}

	@Test
	void deveExigirAlunoAtivo() {
		User inactiveStudent = new User(
				Role.STUDENT, AccountStatus.INACTIVE, "Aluno Inativo", "aluno-inativo@example.com", "ALUNO-2",
				student.getInstitution()
		);
		when(userRepository.findById(inactiveStudent.getId())).thenReturn(Optional.of(inactiveStudent));

		assertApiError(
				HttpStatus.FORBIDDEN,
				"ACCOUNT_INACTIVE",
				() -> service.grant(
						teacher.getId(), assignment.getId(), inactiveStudent.getId(), new CreateExtraAttemptGrantRequest(1)
				)
		);
	}

	@Test
	void deveRejeitarQuantidadeForaDoLimite() {
		assertApiError(
				HttpStatus.UNPROCESSABLE_CONTENT,
				"INVALID_EXTRA_ATTEMPT_QUANTITY",
				() -> service.grant(
						teacher.getId(), assignment.getId(), student.getId(), new CreateExtraAttemptGrantRequest(101)
				)
		);
	}

	private void assertApiError(HttpStatus status, String code, Runnable operation) {
		assertThatThrownBy(operation::run)
				.isInstanceOfSatisfying(ApiException.class, exception -> {
					assertThat(exception.getStatus()).isEqualTo(status);
					assertThat(exception.getCode()).isEqualTo(code);
				});
	}
}
