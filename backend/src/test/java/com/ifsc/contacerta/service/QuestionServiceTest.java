package com.ifsc.contacerta.service;

import com.ifsc.contacerta.dto.question.CreateQuestionRequest;
import com.ifsc.contacerta.dto.question.QuestionOptionRequest;
import com.ifsc.contacerta.dto.question.QuestionResponse;
import com.ifsc.contacerta.dto.question.UpdateQuestionRequest;
import com.ifsc.contacerta.entity.Institution;
import com.ifsc.contacerta.entity.Lesson;
import com.ifsc.contacerta.entity.Question;
import com.ifsc.contacerta.entity.User;
import com.ifsc.contacerta.exception.ApiException;
import com.ifsc.contacerta.model.AccountStatus;
import com.ifsc.contacerta.model.NumericUnit;
import com.ifsc.contacerta.model.QuestionType;
import com.ifsc.contacerta.model.Role;
import com.ifsc.contacerta.repository.LessonRepository;
import com.ifsc.contacerta.repository.LessonAssignmentRepository;
import com.ifsc.contacerta.repository.QuestionRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuestionServiceTest {

	@Test
	void deveRejeitarEscolhaUnicaSemExatamenteUmaOpcaoCorreta() {
		LessonRepository lessonRepository = mock(LessonRepository.class);
		QuestionRepository questionRepository = mock(QuestionRepository.class);
		LessonAssignmentRepository lessonAssignmentRepository = mock(LessonAssignmentRepository.class);
		Institution institution = new Institution("Instituto Exemplo", "11222333000181", "contato@example.com", "48999990000", true);
		User teacher = new User(Role.TEACHER, AccountStatus.ACTIVE, "Professora Ana", "ana@example.com", "PROF-1", institution);
		Lesson lesson = new Lesson("Juros", "Conceitos", "# Teoria", teacher);
		when(lessonRepository.findByIdAndTeacherIdForUpdate(lesson.getId(), teacher.getId())).thenReturn(Optional.of(lesson));
		QuestionService service = new QuestionService(lessonRepository, questionRepository, lessonAssignmentRepository);

		assertThatThrownBy(() -> service.create(teacher.getId(), lesson.getId(), new CreateQuestionRequest(
				"Qual taxa?", QuestionType.SINGLE_CHOICE, null,
				List.of(new QuestionOptionRequest(null, "1%", false), new QuestionOptionRequest(null, "2%", false)),
				null, null, null, null, null
		)))
				.isInstanceOfSatisfying(ApiException.class, exception ->
						assertThat(exception.getCode()).isEqualTo("INVALID_QUESTION_OPTIONS")
				);
	}

	@Test
	void deveTrocarQuestaoNumericaParaVerdadeiroFalsoELimparConfiguracao() {
		LessonRepository lessonRepository = mock(LessonRepository.class);
		QuestionRepository questionRepository = mock(QuestionRepository.class);
		LessonAssignmentRepository lessonAssignmentRepository = mock(LessonAssignmentRepository.class);
		Institution institution = new Institution("Instituto Exemplo", "11222333000181", "contato@example.com", "48999990000", true);
		User teacher = new User(Role.TEACHER, AccountStatus.ACTIVE, "Professora Ana", "ana@example.com", "PROF-1", institution);
		Lesson lesson = new Lesson("Juros", "Conceitos", "# Teoria", teacher);
		Question question = Question.create(lesson, QuestionType.NUMERIC, "Quanto?", null, List.of(), 1);
		question.configureNumeric(new BigDecimal("10.00"), new BigDecimal("0.10"), NumericUnit.BRL, 2);
		when(questionRepository.findByIdAndLessonTeacherId(question.getId(), teacher.getId()))
				.thenReturn(Optional.of(question));
		when(lessonRepository.findByIdAndTeacherIdForUpdate(lesson.getId(), teacher.getId()))
				.thenReturn(Optional.of(lesson));
		QuestionService service = new QuestionService(lessonRepository, questionRepository, lessonAssignmentRepository);

		QuestionResponse response = service.update(teacher.getId(), question.getId(), new UpdateQuestionRequest(
				null, QuestionType.TRUE_FALSE, null, null, true, null, null, null, null, question.getVersion()
		));

		assertThat(response.type()).isEqualTo(QuestionType.TRUE_FALSE);
		assertThat(response.correctBoolean()).isTrue();
		assertThat(response.correctNumericValue()).isNull();
		assertThat(response.absoluteTolerance()).isNull();
		assertThat(response.unit()).isNull();
		assertThat(response.decimalPlaces()).isNull();
		assertThat(response.options()).isEmpty();
	}
}
