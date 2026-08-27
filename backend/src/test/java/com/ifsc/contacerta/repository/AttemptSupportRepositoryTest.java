package com.ifsc.contacerta.repository;

import com.ifsc.contacerta.entity.ExtraAttemptGrant;
import com.ifsc.contacerta.entity.IdempotencyRecord;
import com.ifsc.contacerta.entity.Institution;
import com.ifsc.contacerta.entity.Lesson;
import com.ifsc.contacerta.entity.LessonAssignment;
import com.ifsc.contacerta.entity.Room;
import com.ifsc.contacerta.entity.RoomMembership;
import com.ifsc.contacerta.entity.RoomStudentProgress;
import com.ifsc.contacerta.entity.User;
import com.ifsc.contacerta.model.AccountStatus;
import com.ifsc.contacerta.model.Grade;
import com.ifsc.contacerta.model.Role;
import com.ifsc.contacerta.support.PostgresIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
class AttemptSupportRepositoryTest extends PostgresIntegrationTest {

	@Autowired private InstitutionRepository institutionRepository;
	@Autowired private UserRepository userRepository;
	@Autowired private ExtraAttemptGrantRepository grantRepository;
	@Autowired private RoomStudentProgressRepository progressRepository;
	@Autowired private IdempotencyRecordRepository idempotencyRepository;
	@Autowired private RoomMembershipRepository membershipRepository;
	@Autowired private EntityManager entityManager;

	@Test
	void deveSomarTentativasExtras() {
		Fixture fixture = fixture();
		grantRepository.saveAndFlush(new ExtraAttemptGrant(fixture.assignment(), fixture.student(), fixture.teacher(), 2, Instant.now()));
		grantRepository.saveAndFlush(new ExtraAttemptGrant(fixture.assignment(), fixture.student(), fixture.teacher(), 3, Instant.now()));

		assertThat(grantRepository.sumQuantityByAssignmentIdAndStudentId(fixture.assignment().getId(), fixture.student().getId()))
				.isEqualTo(5L);
	}

	@Test
	void deveAtualizarProgressoComNivelDerivado() {
		Fixture fixture = fixture();
		RoomStudentProgress progress = new RoomStudentProgress(fixture.room(), fixture.student());
		progress.applyResult(120, 3, true, true, Instant.parse("2026-08-27T12:00:00Z"));
		progressRepository.saveAndFlush(progress);

		assertThat(progressRepository.findForUpdateByRoomIdAndStudentId(fixture.room().getId(), fixture.student().getId()))
				.get().satisfies(saved -> {
					assertThat(saved.getTotalXp()).isEqualTo(120);
					assertThat(saved.getLevel()).isEqualTo(2);
					assertThat(saved.getPassedAssignmentCount()).isEqualTo(1);
				});
	}

	@Test
	void deveImpedirChaveIdempotenteDuplicadaPorUsuario() {
		Fixture fixture = fixture();
		idempotencyRepository.saveAndFlush(record(fixture.student(), "attempt-1"));
		idempotencyRepository.save(record(fixture.student(), "attempt-1"));

		assertThatThrownBy(() -> idempotencyRepository.flush()).isInstanceOf(Exception.class);
	}

	@Test
	void deveBuscarRegistroIdempotenteMesmoAposExpirar() {
		Fixture fixture = fixture();
		IdempotencyRecord record = idempotencyRepository.saveAndFlush(record(fixture.student(), "expired"));

		assertThat(idempotencyRepository.findByUserIdAndKey(fixture.student().getId(), "expired"))
				.get().extracting(IdempotencyRecord::getId).isEqualTo(record.getId());
	}

	@Test
	void deveBloquearMatriculaDoAlunoNaSala() {
		Fixture fixture = fixture();
		assertThat(membershipRepository.findForUpdateByRoomIdAndStudentId(fixture.room().getId(), fixture.student().getId()))
				.get().extracting(RoomMembership::getId).isEqualTo(fixture.membership().getId());
	}

	private IdempotencyRecord record(User user, String key) {
		return new IdempotencyRecord(user, "POST", "/student/attempts", key, "hash", 201, "application/json", null, "{}", null,
				Instant.parse("2026-08-27T12:00:00Z"), Instant.parse("2026-08-28T12:00:00Z"));
	}

	private Fixture fixture() {
		Institution institution = institutionRepository.save(new Institution("Instituto", "11222333000181", "contato@example.com", "48999990000", true));
		User teacher = userRepository.save(new User(Role.TEACHER, AccountStatus.ACTIVE, "Ana", "ana@example.com", "P1", institution));
		User student = userRepository.save(new User(Role.STUDENT, AccountStatus.ACTIVE, "Leo", "leo@example.com", "A1", institution));
		Room room = new Room("Sala", null, Grade.HIGH_SCHOOL_1, List.of("Soma"), 50, "ABC234", "hash", teacher, institution);
		Lesson lesson = new Lesson("Licao", null, "# Teoria", teacher);
		entityManager.persist(room);
		entityManager.persist(lesson);
		LessonAssignment assignment = new LessonAssignment(room, lesson, 1, null, null, 30, 3, null, true, true);
		RoomMembership membership = new RoomMembership(room, student);
		entityManager.persist(assignment);
		entityManager.persist(membership);
		entityManager.flush();
		return new Fixture(room, teacher, student, assignment, membership);
	}

	private record Fixture(Room room, User teacher, User student, LessonAssignment assignment, RoomMembership membership) {}
}
