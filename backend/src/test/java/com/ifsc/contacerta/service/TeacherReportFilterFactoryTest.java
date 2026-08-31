package com.ifsc.contacerta.service;

import com.ifsc.contacerta.dto.report.ReportPeriod;
import com.ifsc.contacerta.entity.Institution;
import com.ifsc.contacerta.entity.Room;
import com.ifsc.contacerta.entity.User;
import com.ifsc.contacerta.exception.ApiException;
import com.ifsc.contacerta.model.AccountStatus;
import com.ifsc.contacerta.model.Grade;
import com.ifsc.contacerta.model.MembershipStatus;
import com.ifsc.contacerta.model.ReportFilter;
import com.ifsc.contacerta.model.Role;
import com.ifsc.contacerta.repository.LessonAssignmentRepository;
import com.ifsc.contacerta.repository.LessonRepository;
import com.ifsc.contacerta.repository.RoomMembershipRepository;
import com.ifsc.contacerta.repository.RoomRepository;
import com.ifsc.contacerta.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeacherReportFilterFactoryTest {

	private static final Instant NOW = Instant.parse("2026-08-29T12:00:00Z");

	@Mock private UserRepository userRepository;
	@Mock private RoomRepository roomRepository;
	@Mock private LessonRepository lessonRepository;
	@Mock private LessonAssignmentRepository assignmentRepository;
	@Mock private RoomMembershipRepository membershipRepository;

	private TeacherReportFilterFactory factory;
	private UUID teacherId;
	private UUID roomId;
	private Room room;
	private User teacher;

	@BeforeEach
	void setUp() {
		factory = new TeacherReportFilterFactory(
				userRepository,
				roomRepository,
				lessonRepository,
				assignmentRepository,
				membershipRepository,
				Clock.fixed(NOW, ZoneOffset.UTC)
		);
		Institution institution = new Institution(
				"IFSC", "11222333000181", "ifsc@example.com", "+5548999999999", true
		);
		teacher = new User(
				Role.TEACHER, AccountStatus.ACTIVE, "Professora Ana", "ana@example.com", null, institution
		);
		teacherId = teacher.getId();
		room = new Room(
				"Sala A", null, Grade.HIGH_SCHOOL_1, List.of(), 60,
				"ABC123", "hash", teacher, institution
		);
		roomId = room.getId();
	}

	@Test
	void deveUsarUltimosTrintaDiasQuandoPeriodoNaoForInformado() {
		stubOwnedScope();
		ReportFilter filter = factory.create(teacherId, roomId, null, null, null, null);

		assertThat(filter).isEqualTo(new ReportFilter(
				roomId,
				null,
				Instant.parse("2026-07-30T12:00:00Z"),
				NOW
		));
	}

	@Test
	void deveAceitarPeriodoCompletoSemLimites() {
		stubOwnedScope();
		ReportFilter filter = factory.create(teacherId, roomId, null, ReportPeriod.ALL, null, null);

		assertThat(filter).isEqualTo(new ReportFilter(roomId, null, null, null));
	}

	@Test
	void deveAceitarIntervaloExplicitoSemiaberto() {
		stubOwnedScope();
		Instant from = Instant.parse("2026-08-01T00:00:00Z");
		Instant to = Instant.parse("2026-08-15T00:00:00Z");

		ReportFilter filter = factory.create(teacherId, roomId, null, null, from, to);

		assertThat(filter).isEqualTo(new ReportFilter(roomId, null, from, to));
	}

	@Test
	void deveRejeitarCombinacoesInvalidasDePeriodo() {
		stubOwnedScope();
		Instant from = Instant.parse("2026-08-15T00:00:00Z");
		Instant to = Instant.parse("2026-08-01T00:00:00Z");

		assertValidationError(() -> factory.create(teacherId, roomId, null, null, from, null));
		assertValidationError(() -> factory.create(teacherId, roomId, null, null, from, to));
		assertValidationError(() -> factory.create(teacherId, roomId, null, ReportPeriod.ALL, from, NOW));
	}

	@Test
	void deveOcultarSalaEAulaForaDoEscopo() {
		when(userRepository.findById(teacherId)).thenReturn(Optional.of(teacher));
		UUID foreignRoomId = UUID.randomUUID();
		when(roomRepository.findByIdAndTeacherId(foreignRoomId, teacherId)).thenReturn(Optional.empty());

		assertApiError(
				() -> factory.create(teacherId, foreignRoomId, null, ReportPeriod.ALL, null, null),
				HttpStatus.NOT_FOUND,
				"ROOM_NOT_FOUND"
		);

		UUID lessonId = UUID.randomUUID();
		when(roomRepository.findByIdAndTeacherId(roomId, teacherId)).thenReturn(Optional.of(room));
		when(lessonRepository.findByIdAndTeacherId(lessonId, teacherId)).thenReturn(Optional.empty());
		assertApiError(
				() -> factory.create(teacherId, roomId, lessonId, ReportPeriod.ALL, null, null),
				HttpStatus.NOT_FOUND,
				"LESSON_NOT_FOUND"
		);
	}

	@Test
	void deveExigirProfessorAtivo() {
		User student = new User(
				Role.STUDENT, AccountStatus.ACTIVE, "Aluno", "aluno@example.com", "1", room.getInstitution()
		);
		when(userRepository.findById(student.getId())).thenReturn(Optional.of(student));

		assertApiError(
				() -> factory.create(student.getId(), roomId, null, ReportPeriod.ALL, null, null),
				HttpStatus.FORBIDDEN,
				"TEACHER_REQUIRED"
		);
	}

	@Test
	void deveExigirAlunoAtivoNaSala() {
		UUID studentId = UUID.randomUUID();
		when(membershipRepository.existsByRoomIdAndStudentIdAndStatus(
				roomId, studentId, MembershipStatus.ACTIVE
		)).thenReturn(false);

		assertApiError(
				() -> factory.requireActiveStudent(roomId, studentId),
				HttpStatus.NOT_FOUND,
				"STUDENT_NOT_FOUND"
		);
	}

	private void assertValidationError(Runnable action) {
		assertApiError(action, HttpStatus.UNPROCESSABLE_CONTENT, "VALIDATION_ERROR");
	}

	private void stubOwnedScope() {
		when(userRepository.findById(teacherId)).thenReturn(Optional.of(teacher));
		when(roomRepository.findByIdAndTeacherId(roomId, teacherId)).thenReturn(Optional.of(room));
	}

	private void assertApiError(Runnable action, HttpStatus status, String code) {
		assertThatThrownBy(action::run)
				.isInstanceOfSatisfying(ApiException.class, exception -> {
					assertThat(exception.getStatus()).isEqualTo(status);
					assertThat(exception.getCode()).isEqualTo(code);
				});
	}
}
