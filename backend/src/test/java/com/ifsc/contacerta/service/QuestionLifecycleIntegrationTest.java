package com.ifsc.contacerta.service;

import com.ifsc.contacerta.dto.question.CreateQuestionRequest;
import com.ifsc.contacerta.dto.question.QuestionOptionRequest;
import com.ifsc.contacerta.dto.question.QuestionOrderRequest;
import com.ifsc.contacerta.dto.question.QuestionResponse;
import com.ifsc.contacerta.entity.Institution;
import com.ifsc.contacerta.entity.Lesson;
import com.ifsc.contacerta.entity.Question;
import com.ifsc.contacerta.entity.User;
import com.ifsc.contacerta.model.AccountStatus;
import com.ifsc.contacerta.model.QuestionType;
import com.ifsc.contacerta.model.Role;
import com.ifsc.contacerta.repository.InstitutionRepository;
import com.ifsc.contacerta.repository.LessonRepository;
import com.ifsc.contacerta.repository.QuestionRepository;
import com.ifsc.contacerta.repository.UserRepository;
import com.ifsc.contacerta.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

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
