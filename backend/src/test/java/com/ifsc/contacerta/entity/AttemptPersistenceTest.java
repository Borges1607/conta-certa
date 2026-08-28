package com.ifsc.contacerta.entity;

import com.ifsc.contacerta.model.AccountStatus;
import com.ifsc.contacerta.model.AttemptStatus;
import com.ifsc.contacerta.model.QuestionType;
import com.ifsc.contacerta.model.Grade;
import com.ifsc.contacerta.model.Role;
import com.ifsc.contacerta.repository.InstitutionRepository;
import com.ifsc.contacerta.repository.UserRepository;
import com.ifsc.contacerta.support.PostgresIntegrationTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
class AttemptPersistenceTest extends PostgresIntegrationTest {

	@Autowired
	private InstitutionRepository institutionRepository;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private EntityManager entityManager;

	@Test
	void devePersistirTentativaComSnapshotsImutaveis() {
		Fixture fixture = createFixture("snapshot");
		Attempt attempt = new Attempt(
				fixture.assignment(), fixture.student(), 1,
				Instant.parse("2026-08-27T12:00:00Z"), Instant.parse("2026-08-27T12:30:00Z")
		);
		attempt.addSnapshot(fixture.question(), 1, fixture.question().getOptions());

		entityManager.persist(attempt);
		entityManager.flush();
		entityManager.clear();

		Attempt persisted = entityManager.find(Attempt.class, attempt.getId());
		assertThat(persisted.getSnapshots()).hasSize(1);
		assertThat(persisted.getSnapshots().getFirst().getPrompt()).isEqualTo("Quanto e 2 + 2?");
		assertThat(persisted.getSnapshots().getFirst().getOptions())
				.extracting(AttemptOptionSnapshot::getText)
				.containsExactly("3", "4");
	}

	@Test
	void deveImpedirDuasTentativasAtivas() {
		Fixture fixture = createFixture("ativas");
		entityManager.persist(new Attempt(fixture.assignment(), fixture.student(), 1, Instant.now(), null));
		entityManager.persist(new Attempt(fixture.assignment(), fixture.student(), 2, Instant.now(), null));

		assertThatThrownBy(() -> entityManager.flush()).isInstanceOf(PersistenceException.class);
	}

	@Test
	void deveImpedirSequenciaDuplicada() {
		Fixture fixture = createFixture("sequencia");
		Attempt first = new Attempt(fixture.assignment(), fixture.student(), 1, Instant.now(), null);
		first.finalizeAs(AttemptStatus.SUBMITTED, Instant.now(), 1, 1, 1, true, 1, 10);
		entityManager.persist(first);
		entityManager.flush();

		entityManager.persist(new Attempt(fixture.assignment(), fixture.student(), 1, Instant.now(), null));
		assertThatThrownBy(() -> entityManager.flush()).isInstanceOf(PersistenceException.class);
	}

	@Test
	void devePersistirUmaRespostaPorSnapshot() {
		Fixture fixture = createFixture("resposta");
		Attempt attempt = new Attempt(fixture.assignment(), fixture.student(), 1, Instant.now(), null);
		attempt.addSnapshot(fixture.question(), 1, fixture.question().getOptions());
		entityManager.persist(attempt);
		entityManager.flush();

		AttemptQuestionSnapshot snapshot = attempt.getSnapshots().getFirst();
		AttemptOptionSnapshot option = snapshot.getOptions().get(1);
		entityManager.persist(AttemptAnswer.choice(snapshot, Set.of(option), true, Instant.now()));
		entityManager.flush();
		entityManager.persist(AttemptAnswer.choice(snapshot, Set.of(option), true, Instant.now()));

		assertThatThrownBy(() -> entityManager.flush()).isInstanceOf(PersistenceException.class);
	}

	private Fixture createFixture(String suffix) {
		Institution institution = institutionRepository.save(new Institution(
				"Instituto " + suffix, "11222333000181", suffix + "@example.com", "48999990000", true
		));
		User teacher = userRepository.save(new User(
				Role.TEACHER, AccountStatus.ACTIVE, "Professora Ana", "ana-" + suffix + "@example.com",
				"PROF-" + suffix, institution
		));
		User student = userRepository.save(new User(
				Role.STUDENT, AccountStatus.ACTIVE, "Aluno Leo", "leo-" + suffix + "@example.com",
				"ALU-" + suffix, institution
		));
		Room room = new Room("Sala " + suffix, null, Grade.HIGH_SCHOOL_1, List.of("Soma"), 50,
				"ABC234", "hash-" + suffix, teacher, institution);
		Lesson lesson = new Lesson("Licao " + suffix, null, "# Teoria", teacher);
		entityManager.persist(room);
		entityManager.persist(lesson);
		LessonAssignment assignment = new LessonAssignment(room, lesson, 1, null, null, 30, 3, null, true, true);
		entityManager.persist(assignment);
		Question question = Question.choice(lesson, QuestionType.SINGLE_CHOICE, "Quanto e 2 + 2?", "Some os valores.",
				List.of(new QuestionOptionData("3", false), new QuestionOptionData("4", true)));
		entityManager.persist(question);
		entityManager.flush();
		return new Fixture(student, assignment, question);
	}

	private record Fixture(User student, LessonAssignment assignment, Question question) {
	}
}
