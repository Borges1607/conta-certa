package com.ifsc.contacerta.entity;

import com.ifsc.contacerta.model.AccountStatus;
import com.ifsc.contacerta.model.QuestionType;
import com.ifsc.contacerta.model.Role;
import com.ifsc.contacerta.repository.InstitutionRepository;
import com.ifsc.contacerta.repository.UserRepository;
import com.ifsc.contacerta.support.PostgresIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@Transactional
class LessonPersistenceTest extends PostgresIntegrationTest {

	@Autowired
	private InstitutionRepository institutionRepository;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private EntityManager entityManager;

	@Test
	void devePersistirLicaoComQuestaoDeEscolhaEOpcoesOrdenadas() {
		Institution institution = institutionRepository.save(new Institution(
				"Instituto Exemplo", "11222333000181", "contato@example.com", "48999990000", true
		));
		User teacher = userRepository.save(new User(
				Role.TEACHER, AccountStatus.ACTIVE, "Professora Ana", "ana@example.com", "PROF-1", institution
		));
		Lesson lesson = new Lesson("Juros compostos", "Conceitos", "# Teoria", teacher);
		Question question = Question.choice(
				lesson,
				QuestionType.SINGLE_CHOICE,
				"Qual é a taxa mensal?",
				"Leia o enunciado novamente.",
				List.of(new QuestionOptionData("1%", true), new QuestionOptionData("10%", false))
		);
		entityManager.persist(lesson);
		entityManager.persist(question);
		entityManager.flush();
		entityManager.clear();

		Question persisted = entityManager.find(Question.class, question.getId());

		assertThat(persisted.getLesson().getId()).isEqualTo(lesson.getId());
		assertThat(persisted.getType()).isEqualTo(QuestionType.SINGLE_CHOICE);
		assertThat(persisted.getOptions())
				.extracting(QuestionOption::getText, QuestionOption::isCorrect)
				.containsExactly(tuple("1%", true), tuple("10%", false));
	}
}
