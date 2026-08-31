package com.ifsc.contacerta.controller;

import com.ifsc.contacerta.exception.ApiException;
import com.ifsc.contacerta.model.Role;
import com.ifsc.contacerta.security.CurrentUser;
import com.ifsc.contacerta.service.QuestionService;
import com.ifsc.contacerta.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.Clock;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TeacherQuestionControllerTest {

	private QuestionService service;
	private MockMvc mockMvc;
	private UUID teacherId;

	@BeforeEach
	void setUp() {
		service = mock(QuestionService.class);
		teacherId = UUID.randomUUID();
		CurrentUser currentUser = new CurrentUser(teacherId, Role.TEACHER, UUID.randomUUID());
		mockMvc = MockMvcBuilders.standaloneSetup(
				new TeacherQuestionController(service),
				new TeacherQuestionMutationController(service)
		)
				.setControllerAdvice(new GlobalExceptionHandler(Clock.systemUTC()))
				.setCustomArgumentResolvers(currentUserResolver(currentUser))
				.build();
	}

	@Test
	void deveValidarAlternativaAninhada() throws Exception {
		mockMvc.perform(post("/teacher/lessons/{lessonId}/questions", UUID.randomUUID())
					.contentType("application/json")
					.content("""
							{"prompt":"Pergunta","type":"SINGLE_CHOICE","options":[{"text":"","correct":true}]}
							"""))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("options[0].text"));
	}

	@Test
	void deveValidarEscalaNumericaDoPatch() throws Exception {
		mockMvc.perform(patch("/teacher/questions/{questionId}", UUID.randomUUID())
					.contentType("application/json")
					.content("""
							{"correctNumericValue":1.1234567,"version":0}
							"""))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}

	@Test
	void deveExporErroDeAulaArquivada() throws Exception {
		UUID questionId = UUID.randomUUID();
		doThrow(new ApiException(
				HttpStatus.UNPROCESSABLE_CONTENT, "LESSON_ARCHIVED", "Archived lessons are read-only."
		)).when(service).update(eq(teacherId), eq(questionId), any());

		mockMvc.perform(patch("/teacher/questions/{questionId}", questionId)
					.contentType("application/json")
					.content("{\"prompt\":\"Novo\",\"version\":0}"))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.code").value("LESSON_ARCHIVED"));
	}

	private HandlerMethodArgumentResolver currentUserResolver(CurrentUser currentUser) {
		return new HandlerMethodArgumentResolver() {
			@Override
			public boolean supportsParameter(MethodParameter parameter) {
				return parameter.getParameterType() == CurrentUser.class;
			}

			@Override
			public Object resolveArgument(
					MethodParameter parameter,
					ModelAndViewContainer mavContainer,
					NativeWebRequest webRequest,
					WebDataBinderFactory binderFactory
			) {
				return currentUser;
			}
		};
	}
}
