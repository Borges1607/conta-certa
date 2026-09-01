package com.ifsc.contacerta.service;

import com.ifsc.contacerta.dto.studentlesson.StudentLessonPathResponse;
import com.ifsc.contacerta.entity.Attempt;
import com.ifsc.contacerta.entity.Institution;
import com.ifsc.contacerta.entity.Lesson;
import com.ifsc.contacerta.entity.LessonAssignment;
import com.ifsc.contacerta.entity.Room;
import com.ifsc.contacerta.entity.RoomMembership;
import com.ifsc.contacerta.entity.User;
import com.ifsc.contacerta.exception.ApiException;
import com.ifsc.contacerta.model.AccountStatus;
import com.ifsc.contacerta.model.AttemptAvailabilityStatus;
import com.ifsc.contacerta.model.AttemptStatus;
import com.ifsc.contacerta.model.ContentStatus;
import com.ifsc.contacerta.model.Grade;
import com.ifsc.contacerta.model.LessonLockReason;
import com.ifsc.contacerta.model.MembershipStatus;
import com.ifsc.contacerta.model.Role;
import com.ifsc.contacerta.repository.AttemptRepository;
import com.ifsc.contacerta.repository.ExtraAttemptGrantRepository;
import com.ifsc.contacerta.repository.LessonAssignmentRepository;
import com.ifsc.contacerta.repository.QuestionRepository;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StudentLessonServiceTest {

	private static final Instant NOW = Instant.parse("2026-08-28T12:00:00Z");

	private RoomMembershipRepository membershipRepository;
	private LessonAssignmentRepository assignmentRepository;
	private AttemptRepository attemptRepository;
	private ExtraAttemptGrantRepository grantRepository;
	private UserRepository userRepository;
	private StudentLessonService service;

	@BeforeEach
	void setUp() {
		membershipRepository = mock(RoomMembershipRepository.class);
		assignmentRepository = mock(LessonAssignmentRepository.class);
		attemptRepository = mock(AttemptRepository.class);
		grantRepository = mock(ExtraAttemptGrantRepository.class);
		userRepository = mock(UserRepository.class);
		service = new StudentLessonService(
				membershipRepository,
				assignmentRepository,
				attemptRepository,
				grantRepository,
				userRepository,
				mock(QuestionRepository.class),
				Clock.fixed(NOW, ZoneOffset.UTC)
		);
	}

	@Test
	void deveInformarMotivoQuandoLicaoAindaNaoAbriu() {
		Fixture fixture = fixture(NOW.plusSeconds(60), null);
		stubPath(fixture);

		StudentLessonPathResponse item = service.path(fixture.student().getId(), fixture.room().getId()).getFirst();

		assertThat(item.availability()).isEqualTo(AttemptAvailabilityStatus.LOCKED);
		assertThat(item.lockReason()).isEqualTo(LessonLockReason.NOT_YET_AVAILABLE);
		assertThat(item.rules().passingScorePercent()).isEqualTo(60);
	}

	@Test
	void devePriorizarTentativaAtivaSobreLimiteEManterDadosParaRetomada() {
		Fixture fixture = fixture(null, NOW.plusSeconds(600));
		Attempt active = new Attempt(fixture.assignment(), fixture.student(), 1, NOW.minusSeconds(30), NOW.plusSeconds(300));
		stubPath(fixture);
		when(attemptRepository.countByAssignmentIdAndStudentId(fixture.assignment().getId(), fixture.student().getId()))
				.thenReturn(1L);
		when(attemptRepository.findByAssignmentIdAndStudentIdAndStatus(
				fixture.assignment().getId(), fixture.student().getId(), AttemptStatus.IN_PROGRESS
		)).thenReturn(Optional.of(active));

		StudentLessonPathResponse item = service.path(fixture.student().getId(), fixture.room().getId()).getFirst();

		assertThat(item.availability()).isEqualTo(AttemptAvailabilityStatus.IN_PROGRESS);
		assertThat(item.lockReason()).isNull();
		assertThat(item.activeAttemptId()).isEqualTo(active.getId());
		assertThat(item.activeAttemptExpiresAt()).isEqualTo(active.getExpiresAt());
	}

	@Test
	void deveOcultarDetalheQuandoAtribuicaoNaoEstiverNaMatriculaAtiva() {
		Fixture fixture = fixture(null, null);
		UUID studentId = fixture.student().getId();
		UUID roomId = fixture.room().getId();
		UUID lessonId = fixture.assignment().getLesson().getId();
		when(userRepository.findById(studentId)).thenReturn(Optional.of(fixture.student()));
		when(membershipRepository.findByRoomIdAndStudentId(roomId, studentId))
				.thenReturn(Optional.of(new RoomMembership(fixture.room(), fixture.student())));
		when(assignmentRepository.findAccessibleByRoomIdAndLessonIdAndStudentId(
				roomId, lessonId, studentId, MembershipStatus.ACTIVE
		)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.detail(studentId, roomId, lessonId))
				.isInstanceOfSatisfying(ApiException.class, error -> {
					assertThat(error.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
					assertThat(error.getCode()).isEqualTo("ASSIGNMENT_NOT_FOUND");
				});
	}

	private void stubPath(Fixture fixture) {
		UUID studentId = fixture.student().getId();
		UUID roomId = fixture.room().getId();
		UUID assignmentId = fixture.assignment().getId();
		when(userRepository.findById(studentId)).thenReturn(Optional.of(fixture.student()));
		when(membershipRepository.findByRoomIdAndStudentId(roomId, studentId))
				.thenReturn(Optional.of(new RoomMembership(fixture.room(), fixture.student())));
		when(assignmentRepository.findAccessibleByRoomIdAndStudentIdAndStatusOrderByPositionAsc(
				roomId, studentId, MembershipStatus.ACTIVE, ContentStatus.PUBLISHED
		))
				.thenReturn(List.of(fixture.assignment()));
		when(attemptRepository.findFirstByAssignmentIdAndStudentIdAndStatusInOrderByScorePercentDescSubmittedAtAsc(
				assignmentId, studentId, List.of(AttemptStatus.SUBMITTED, AttemptStatus.EXPIRED)
		)).thenReturn(Optional.empty());
		when(attemptRepository.findByAssignmentIdAndStudentIdAndStatus(assignmentId, studentId, AttemptStatus.IN_PROGRESS))
				.thenReturn(Optional.empty());
	}

	private Fixture fixture(Instant availableFrom, Instant dueAt) {
		Institution institution = new Institution("Instituto", "11222333000181", "contato@example.com", "48999990000", true);
		User teacher = new User(Role.TEACHER, AccountStatus.ACTIVE, "Professora", "prof@example.com", "P-1", institution);
		User student = new User(Role.STUDENT, AccountStatus.ACTIVE, "Aluno", "aluno@example.com", "A-1", institution);
		Room room = new Room("Sala", null, Grade.HIGH_SCHOOL_1, List.of("Frações"), 60, "ABC234", "hash", teacher, institution);
		Lesson lesson = new Lesson("Frações", "Resumo", "# Teoria", teacher);
		LessonAssignment assignment = new LessonAssignment(room, lesson, 1, availableFrom, dueAt, 30, 1, 10, false, false);
		assignment.publish();
		return new Fixture(student, room, assignment);
	}

	private record Fixture(User student, Room room, LessonAssignment assignment) {}
}
