package com.ifsc.contacerta.service;

import com.ifsc.contacerta.dto.question.CreateQuestionRequest;
import com.ifsc.contacerta.dto.question.QuestionOptionRequest;
import com.ifsc.contacerta.dto.question.QuestionOrderRequest;
import com.ifsc.contacerta.dto.question.QuestionResponse;
import com.ifsc.contacerta.dto.question.UpdateQuestionRequest;
import com.ifsc.contacerta.entity.Attempt;
import com.ifsc.contacerta.entity.Institution;
import com.ifsc.contacerta.entity.Lesson;
import com.ifsc.contacerta.entity.LessonAssignment;
import com.ifsc.contacerta.entity.Question;
import com.ifsc.contacerta.entity.Room;
import com.ifsc.contacerta.entity.User;
import com.ifsc.contacerta.exception.ApiException;
import com.ifsc.contacerta.model.AccountStatus;
import com.ifsc.contacerta.model.AttemptStatus;
import com.ifsc.contacerta.model.Grade;
import com.ifsc.contacerta.model.QuestionType;
import com.ifsc.contacerta.model.Role;
import com.ifsc.contacerta.repository.InstitutionRepository;
import com.ifsc.contacerta.repository.AttemptRepository;
import com.ifsc.contacerta.repository.LessonAssignmentRepository;
import com.ifsc.contacerta.repository.LessonRepository;
import com.ifsc.contacerta.repository.QuestionRepository;
import com.ifsc.contacerta.repository.RoomRepository;
import com.ifsc.contacerta.repository.UserRepository;
import com.ifsc.contacerta.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuestionLifecycleIntegrationTest extends PostgresIntegrationTest {

	@Autowired
	private QuestionService questionService;
	@Autowired
	private InstitutionRepository institutionRepository;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private LessonRepository lessonRepository;
	@Autowired
	private QuestionRepository questionRepository;
	@Autowired
	private RoomRepository roomRepository;
	@Autowired
	private LessonAssignmentRepository lessonAssignmentRepository;
	@Autowired
	private AttemptRepository attemptRepository;
	@Autowired
	private EntityManager entityManager;

	@Test
	void deveCriarSegundaQuestaoNaPosicaoDois() {
		Fixture fixture = createFixture();

		questionService.create(fixture.teacherId(), fixture.lessonId(), choice("Primeira"));
		questionService.create(fixture.teacherId(), fixture.lessonId(), choice("Segunda"));

		assertThat(questionRepository.findByLessonIdOrderByPositionAsc(fixture.lessonId()))
				.extracting(Question::getPosition)
				.containsExactly(1, 2);
	}

	@Test
	void deveTrocarPosicoesSemViolarRestricaoUnica() {
		Fixture fixture = createFixture();
		QuestionResponse first = questionService.create(fixture.teacherId(), fixture.lessonId(), choice("Primeira"));
		QuestionResponse second = questionService.create(fixture.teacherId(), fixture.lessonId(), choice("Segunda"));

		questionService.reorder(
				fixture.teacherId(),
				fixture.lessonId(),
				new QuestionOrderRequest(List.of(second.id(), first.id()))
		);

		assertThat(questionRepository.findByLessonIdOrderByPositionAsc(fixture.lessonId()))
				.extracting(Question::getId)
				.containsExactly(second.id(), first.id());
	}

	@Test
	void deveSerializarCriacoesConcorrentesNaMesmaAula() throws Exception {
		Fixture fixture = createFixture();
		CountDownLatch start = new CountDownLatch(1);
		try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
			Future<?> first = executor.submit(() -> createAfter(start, fixture, "Primeira"));
			Future<?> second = executor.submit(() -> createAfter(start, fixture, "Segunda"));

			start.countDown();
			first.get(10, TimeUnit.SECONDS);
			second.get(10, TimeUnit.SECONDS);
		}

		assertThat(questionRepository.findByLessonIdOrderByPositionAsc(fixture.lessonId()))
				.extracting(Question::getPosition)
				.containsExactly(1, 2);
	}

	@Test
	@Transactional
	void devePreservarSnapshotExistenteEAplicarEdicaoEmSnapshotFuturo() {
		Fixture fixture = createFixture();
		QuestionResponse created = questionService.create(fixture.teacherId(), fixture.lessonId(), choice("Antes"));
		Question question = questionRepository.findById(created.id()).orElseThrow();
		User teacher = userRepository.findById(fixture.teacherId()).orElseThrow();
		Institution institution = teacher.getInstitution();
		User student = userRepository.save(new User(
				Role.STUDENT, AccountStatus.ACTIVE, "Estudante", "aluno@example.com", "ALUNO-1", institution
		));
		Room room = roomRepository.save(new Room(
				"Turma", null, Grade.HIGH_SCHOOL_1, List.of(), 60, "ABC123", "hash-snapshot", teacher, institution
		));
		LessonAssignment assignment = lessonAssignmentRepository.save(new LessonAssignment(
				room, lessonRepository.findById(fixture.lessonId()).orElseThrow(), 1,
				null, null, null, null, null, false, false
		));
		Attempt oldAttempt = new Attempt(assignment, student, 1, Instant.now(), null);
		oldAttempt.addSnapshot(question, 1, question.getOptions());
		oldAttempt.finalizeAs(AttemptStatus.SUBMITTED, Instant.now(), 1, 1, 1, true, 3, 10);
		attemptRepository.save(oldAttempt);

		questionService.update(fixture.teacherId(), question.getId(), new UpdateQuestionRequest(
				"Depois", null, null, null, null, null, null, null, null, created.version()
		));
		Question updated = questionRepository.findById(question.getId()).orElseThrow();
		Attempt futureAttempt = new Attempt(assignment, student, 2, Instant.now(), null);
		futureAttempt.addSnapshot(updated, 1, updated.getOptions());
		attemptRepository.save(futureAttempt);
		entityManager.flush();
		entityManager.clear();

		assertThat(attemptRepository.findById(oldAttempt.getId()).orElseThrow().getSnapshots().getFirst().getPrompt())
				.isEqualTo("Antes");
		assertThat(attemptRepository.findById(futureAttempt.getId()).orElseThrow().getSnapshots().getFirst().getPrompt())
				.isEqualTo("Depois");
	}

	@Test
	void deveExcluirFisicamenteQuestaoNuncaUtilizada() {
		Fixture fixture = createFixture();
		QuestionResponse created = questionService.create(fixture.teacherId(), fixture.lessonId(), choice("Descartável"));

		questionService.delete(fixture.teacherId(), created.id());

		assertThat(questionRepository.findById(created.id())).isEmpty();
	}

	@Test
	void deveArquivarQuestaoDeAulaAtribuida() {
		Fixture fixture = createFixture();
		QuestionResponse created = questionService.create(fixture.teacherId(), fixture.lessonId(), choice("Utilizada"));
		assignLesson(fixture);

		questionService.delete(fixture.teacherId(), created.id());
		questionService.delete(fixture.teacherId(), created.id());

		assertThat(questionRepository.findById(created.id())).get()
				.extracting(Question::isActive)
				.isEqualTo(false);
	}

	@Test
	void deveBloquearCriacaoEmAulaArquivada() {
		Fixture fixture = createFixture();
		Lesson lesson = lessonRepository.findById(fixture.lessonId()).orElseThrow();
		lesson.archive();
		lessonRepository.save(lesson);

		assertThatThrownBy(() -> questionService.create(
				fixture.teacherId(), fixture.lessonId(), choice("Proibida")
		)).isInstanceOfSatisfying(ApiException.class, exception -> {
			assertThat(exception.getStatus().value()).isEqualTo(422);
			assertThat(exception.getCode()).isEqualTo("LESSON_ARCHIVED");
		});
	}

	private LessonAssignment assignLesson(Fixture fixture) {
		User teacher = userRepository.findById(fixture.teacherId()).orElseThrow();
		Room room = roomRepository.save(new Room(
				"Turma atribuída", null, Grade.HIGH_SCHOOL_1, List.of(), 60,
				"DEF456", "hash-assignment", teacher, teacher.getInstitution()
		));
		return lessonAssignmentRepository.save(new LessonAssignment(
				room, lessonRepository.findById(fixture.lessonId()).orElseThrow(), 1,
				null, null, null, null, null, false, false
		));
	}

	private void createAfter(CountDownLatch start, Fixture fixture, String prompt) {
		try {
			start.await(5, TimeUnit.SECONDS);
			questionService.create(fixture.teacherId(), fixture.lessonId(), choice(prompt));
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(exception);
		}
	}

	private Fixture createFixture() {
		Institution institution = institutionRepository.save(new Institution(
				"Instituto Exemplo", "11222333000181", "contato@example.com", "48999990000", true
		));
		User teacher = userRepository.save(new User(
				Role.TEACHER, AccountStatus.ACTIVE, "Professora Ana", "ana@example.com", "PROF-1", institution
		));
		Lesson lesson = lessonRepository.save(new Lesson("Juros", "Resumo", "# Teoria", teacher));
		return new Fixture(teacher.getId(), lesson.getId());
	}

	private CreateQuestionRequest choice(String prompt) {
		return new CreateQuestionRequest(
				prompt,
				QuestionType.SINGLE_CHOICE,
				null,
				List.of(
						new QuestionOptionRequest(null, "Correta", true),
						new QuestionOptionRequest(null, "Incorreta", false)
				),
				null,
				null,
				null,
				null,
				null
		);
	}

	private record Fixture(UUID teacherId, UUID lessonId) {
	}
}
