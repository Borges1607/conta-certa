package com.ifsc.contacerta.service;

import com.ifsc.contacerta.dto.lesson.CreateLessonRequest;
import com.ifsc.contacerta.entity.Institution;
import com.ifsc.contacerta.entity.Lesson;
import com.ifsc.contacerta.entity.User;
import com.ifsc.contacerta.model.AccountStatus;
import com.ifsc.contacerta.model.ContentStatus;
import com.ifsc.contacerta.model.Role;
import com.ifsc.contacerta.repository.LessonRepository;
import com.ifsc.contacerta.repository.QuestionRepository;
import com.ifsc.contacerta.repository.UserRepository;
import com.ifsc.contacerta.exception.ApiException;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LessonServiceTest {

	@Test
	void deveCriarLicaoRascunhoNoAcervoDoProfessorAtivo() {
		UserRepository userRepository = mock(UserRepository.class);
		LessonRepository lessonRepository = mock(LessonRepository.class);
		Institution institution = new Institution(
				"Instituto Exemplo", "11222333000181", "contato@example.com", "48999990000", true
		);
		User teacher = new User(
				Role.TEACHER, AccountStatus.ACTIVE, "Professora Ana", "ana@example.com", "PROF-1", institution
		);
		when(userRepository.findById(teacher.getId())).thenReturn(Optional.of(teacher));
		when(lessonRepository.save(any(Lesson.class))).thenAnswer(invocation -> invocation.getArgument(0));
		LessonService service = new LessonService(userRepository, lessonRepository, mock(QuestionRepository.class));

		var response = service.create(teacher.getId(), new CreateLessonRequest(
				"Juros compostos", "Conceitos", "# Teoria"
		));

		assertThat(response.title()).isEqualTo("Juros compostos");
		assertThat(response.status()).isEqualTo(ContentStatus.DRAFT);
		assertThat(response.questionCount()).isZero();
		assertThat(response.assignmentCount()).isZero();
	}

	@Test
	void deveImpedirPublicacaoDeLicaoSemQuestaoAtiva() {
		UserRepository userRepository = mock(UserRepository.class);
		LessonRepository lessonRepository = mock(LessonRepository.class);
		Institution institution = new Institution(
				"Instituto Exemplo", "11222333000181", "contato@example.com", "48999990000", true
		);
		User teacher = new User(
				Role.TEACHER, AccountStatus.ACTIVE, "Professora Ana", "ana@example.com", "PROF-1", institution
		);
		Lesson lesson = new Lesson("Juros compostos", "Conceitos", "# Teoria", teacher);
		when(userRepository.findById(teacher.getId())).thenReturn(Optional.of(teacher));
		when(lessonRepository.findByIdAndTeacherId(lesson.getId(), teacher.getId())).thenReturn(Optional.of(lesson));
		LessonService service = new LessonService(userRepository, lessonRepository, mock(QuestionRepository.class));

		assertThatThrownBy(() -> service.publish(teacher.getId(), lesson.getId()))
				.isInstanceOfSatisfying(ApiException.class, exception -> {
					assertThat(exception.getStatus().value()).isEqualTo(422);
					assertThat(exception.getCode()).isEqualTo("LESSON_HAS_NO_ACTIVE_QUESTIONS");
				});
	}
}
