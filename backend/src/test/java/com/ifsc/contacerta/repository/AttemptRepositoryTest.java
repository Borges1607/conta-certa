package com.ifsc.contacerta.repository;

import com.ifsc.contacerta.entity.Attempt;
import com.ifsc.contacerta.entity.Institution;
import com.ifsc.contacerta.entity.Lesson;
import com.ifsc.contacerta.entity.LessonAssignment;
import com.ifsc.contacerta.entity.Room;
import com.ifsc.contacerta.entity.User;
import com.ifsc.contacerta.model.AccountStatus;
import com.ifsc.contacerta.model.AttemptStatus;
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

@Transactional
class AttemptRepositoryTest extends PostgresIntegrationTest {

	@Autowired
	private InstitutionRepository institutionRepository;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private AttemptRepository attemptRepository;
	@Autowired
	private EntityManager entityManager;

	@Test
	void deveBuscarTentativaAtivaDoAlunoPorAtribuicao() {
		Fixture fixture = createFixture();
		Attempt attempt = new Attempt(fixture.assignment(), fixture.student(), 1, Instant.now(), null);
		attemptRepository.saveAndFlush(attempt);

		assertThat(attemptRepository.findByAssignmentIdAndStudentIdAndStatus(
				fixture.assignment().getId(), fixture.student().getId(), AttemptStatus.IN_PROGRESS
		)).get().extracting(Attempt::getId).isEqualTo(attempt.getId());
		assertThat(attemptRepository.findByIdForUpdate(attempt.getId())).get()
				.extracting(Attempt::getId).isEqualTo(attempt.getId());
	}

	private Fixture createFixture() {
		Institution institution = institutionRepository.save(new Institution(
				"Instituto", "11222333000181", "contato@example.com", "48999990000", true
		));
		User teacher = userRepository.save(new User(
				Role.TEACHER, AccountStatus.ACTIVE, "Professora Ana", "ana@example.com", "PROF-1", institution
		));
		User student = userRepository.save(new User(
				Role.STUDENT, AccountStatus.ACTIVE, "Aluno Leo", "leo@example.com", "ALU-1", institution
		));
		Room room = new Room("Sala", null, Grade.HIGH_SCHOOL_1, List.of("Soma"), 50, "ABC234", "hash", teacher, institution);
		Lesson lesson = new Lesson("Licao", null, "# Teoria", teacher);
		entityManager.persist(room);
		entityManager.persist(lesson);
		LessonAssignment assignment = new LessonAssignment(room, lesson, 1, null, null, 30, 3, null, true, true);
		entityManager.persist(assignment);
		entityManager.flush();
		return new Fixture(student, assignment);
	}

	private record Fixture(User student, LessonAssignment assignment) {
	}
}
