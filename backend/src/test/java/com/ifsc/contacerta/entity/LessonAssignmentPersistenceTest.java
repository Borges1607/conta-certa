package com.ifsc.contacerta.entity;

import com.ifsc.contacerta.model.AccountStatus;
import com.ifsc.contacerta.model.ContentStatus;
import com.ifsc.contacerta.model.Grade;
import com.ifsc.contacerta.model.Role;
import com.ifsc.contacerta.repository.InstitutionRepository;
import com.ifsc.contacerta.repository.UserRepository;
import com.ifsc.contacerta.support.PostgresIntegrationTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
class LessonAssignmentPersistenceTest extends PostgresIntegrationTest {

	@Autowired
	private InstitutionRepository institutionRepository;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private EntityManager entityManager;

	@Test
	void devePersistirAtribuicaoComConfiguracao() {
		Fixture fixture = createFixture("persistencia");
		Instant availableFrom = Instant.parse("2026-09-01T12:00:00Z");
		Instant dueAt = Instant.parse("2026-09-10T12:00:00Z");
		LessonAssignment assignment = new LessonAssignment(
				fixture.room(), fixture.lesson(), 1, availableFrom, dueAt, 30, 3, 5, true, false
		);

		entityManager.persist(assignment);
		entityManager.flush();
		entityManager.clear();

		LessonAssignment persisted = entityManager.find(LessonAssignment.class, assignment.getId());
		assertThat(persisted.getRoom().getId()).isEqualTo(fixture.room().getId());
		assertThat(persisted.getLesson().getId()).isEqualTo(fixture.lesson().getId());
		assertThat(persisted.getPosition()).isEqualTo(1);
		assertThat(persisted.getStatus()).isEqualTo(ContentStatus.DRAFT);
		assertThat(persisted.getAvailableFrom()).isEqualTo(availableFrom);
		assertThat(persisted.getDueAt()).isEqualTo(dueAt);
		assertThat(persisted.getTimeLimitMinutes()).isEqualTo(30);
		assertThat(persisted.getMaxAttempts()).isEqualTo(3);
		assertThat(persisted.getQuestionCount()).isEqualTo(5);
		assertThat(persisted.isShuffleQuestions()).isTrue();
		assertThat(persisted.isShuffleOptions()).isFalse();
		assertThat(persisted.getCreatedAt()).isNotNull();
		assertThat(persisted.getUpdatedAt()).isNotNull();
		assertThat(persisted.getVersion()).isZero();
	}

	@Test
	void deveDetectarAtualizacaoComVersaoDesatualizada() {
		Fixture fixture = createFixture("versao");
		LessonAssignment assignment = new LessonAssignment(
				fixture.room(), fixture.lesson(), 1, null, null, 30, 3, null, true, true
		);
		entityManager.persist(assignment);
		entityManager.flush();
		entityManager.clear();

		LessonAssignment firstCopy = entityManager.find(LessonAssignment.class, assignment.getId());
		entityManager.detach(firstCopy);
		LessonAssignment secondCopy = entityManager.find(LessonAssignment.class, assignment.getId());
		secondCopy.moveTo(2);
		entityManager.flush();
		entityManager.detach(secondCopy);

		firstCopy.moveTo(3);
		assertThatThrownBy(() -> entityManager.merge(firstCopy))
				.isInstanceOf(OptimisticLockException.class);
	}

	private Fixture createFixture(String suffix) {
		Institution institution = institutionRepository.save(new Institution(
				"Instituto " + suffix, "11222333000181", suffix + "@example.com", "48999990000", true
		));
		User teacher = userRepository.save(new User(
				Role.TEACHER, AccountStatus.ACTIVE, "Professora Ana", "ana-" + suffix + "@example.com",
				"PROF-" + suffix, institution
		));
		Room room = new Room(
				"Sala " + suffix, null, Grade.HIGH_SCHOOL_1, List.of("Porcentagem"), 50,
				"ABC234", "hash-" + suffix, teacher, institution
		);
		Lesson lesson = new Lesson("Lição " + suffix, null, "# Teoria", teacher);
		entityManager.persist(room);
		entityManager.persist(lesson);
		return new Fixture(room, lesson);
	}

	private record Fixture(Room room, Lesson lesson) {
	}
}
