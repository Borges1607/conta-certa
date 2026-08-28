package com.ifsc.contacerta.controller;

import com.ifsc.contacerta.dto.shared.PageResponse;
import com.ifsc.contacerta.dto.studentlesson.AttemptHistoryResponse;
import com.ifsc.contacerta.dto.studentlesson.LessonRulesResponse;
import com.ifsc.contacerta.dto.studentlesson.StudentLessonDetailResponse;
import com.ifsc.contacerta.dto.studentlesson.StudentLessonPathResponse;
import com.ifsc.contacerta.model.AttemptAvailabilityStatus;
import com.ifsc.contacerta.model.AttemptStatus;
import com.ifsc.contacerta.model.Role;
import com.ifsc.contacerta.security.CurrentUser;
import com.ifsc.contacerta.service.StudentLessonService;
import com.ifsc.contacerta.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StudentLessonControllerTest {

	private StudentLessonService lessonService;
	private MockMvc mockMvc;
	private UUID studentId;

	@BeforeEach
	void setUp() {
		lessonService = mock(StudentLessonService.class);
		studentId = UUID.randomUUID();
		CurrentUser currentUser = new CurrentUser(studentId, Role.STUDENT, UUID.randomUUID());
		mockMvc = MockMvcBuilders.standaloneSetup(new StudentLessonController(lessonService))
				.setCustomArgumentResolvers(currentUserResolver(currentUser))
				.setControllerAdvice(new GlobalExceptionHandler(Clock.systemUTC()))
				.build();
	}

	@Test
	void deveExporTrilhaNoContratoConsumidoPeloFrontend() throws Exception {
		UUID roomId = UUID.randomUUID();
		UUID assignmentId = UUID.randomUUID();
		UUID lessonId = UUID.randomUUID();
		StudentLessonPathResponse item = new StudentLessonPathResponse(
				assignmentId,
				lessonId,
				"Frações",
				"Resumo",
				2,
				AttemptAvailabilityStatus.AVAILABLE,
				null,
				null,
				Instant.parse("2026-09-01T12:00:00Z"),
				new LessonRulesResponse(30, 3, 1, 2L, 10, 60),
				80,
				3,
				null,
				null,
				UUID.randomUUID()
		);
		when(lessonService.path(studentId, roomId)).thenReturn(List.of(item));

		mockMvc.perform(get("/student/rooms/{roomId}/lessons", roomId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].order").value(2))
				.andExpect(jsonPath("$[0].lockReason").hasJsonPath())
				.andExpect(jsonPath("$[0].rules.timeLimitMinutes").exists())
				.andExpect(jsonPath("$[0].rules.attemptsRemaining").value(2))
				.andExpect(jsonPath("$[0].rules.passingScorePercent").isNumber())
				.andExpect(jsonPath("$[0].activeAttemptId").hasJsonPath())
				.andExpect(jsonPath("$[0].bestAttemptId").exists())
				.andExpect(jsonPath("$[0].position").doesNotExist());
	}

	@Test
	void deveExporDetalheDaLicaoNoContratoConsumidoPeloFrontend() throws Exception {
		UUID roomId = UUID.randomUUID();
		UUID assignmentId = UUID.randomUUID();
		UUID lessonId = UUID.randomUUID();
		StudentLessonDetailResponse detail = new StudentLessonDetailResponse(
				assignmentId,
				lessonId,
				roomId,
				"Frações",
				"Resumo",
				"# Teoria",
				List.of(),
				AttemptAvailabilityStatus.AVAILABLE,
				null,
				null,
				null,
				new LessonRulesResponse(30, 3, 1, 2L, 10, 60),
				80,
				3,
				null,
				UUID.randomUUID()
		);
		when(lessonService.detail(studentId, roomId, lessonId)).thenReturn(detail);

		mockMvc.perform(get("/student/rooms/{roomId}/lessons/{lessonId}", roomId, lessonId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.roomId").value(roomId.toString()))
				.andExpect(jsonPath("$.materials").isArray())
				.andExpect(jsonPath("$.rules.questionCount").value(10))
				.andExpect(jsonPath("$.bestScorePercent").value(80))
				.andExpect(jsonPath("$.activeAttemptId").hasJsonPath());
	}

	@Test
	void deveExporHistoricoPelaRotaDaSalaELicao() throws Exception {
		UUID roomId = UUID.randomUUID();
		UUID lessonId = UUID.randomUUID();
		UUID attemptId = UUID.randomUUID();
		AttemptHistoryResponse item = new AttemptHistoryResponse(
				attemptId,
				AttemptStatus.SUBMITTED,
				Instant.parse("2026-08-28T12:00:00Z"),
				Instant.parse("2026-08-28T12:20:00Z"),
				70,
				2,
				true,
				7,
				10,
				true
		);
		when(lessonService.history(studentId, roomId, lessonId, PageRequest.of(0, 20)))
				.thenReturn(new PageResponse<>(List.of(item), 0, 20, 1, 1));

		mockMvc.perform(get("/student/rooms/{roomId}/lessons/{lessonId}/attempts", roomId, lessonId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].attemptId").value(attemptId.toString()))
				.andExpect(jsonPath("$.content[0].stars").isNumber())
				.andExpect(jsonPath("$.content[0].correctAnswers").isNumber())
				.andExpect(jsonPath("$.content[0].totalQuestions").isNumber())
				.andExpect(jsonPath("$.content[0].best").value(true))
				.andExpect(jsonPath("$.content[0].id").doesNotExist());
	}

	@Test
	void deveRemoverRotaAntigaDeHistorico() throws Exception {
		mockMvc.perform(get("/student/room-lessons/{assignmentId}/attempts", UUID.randomUUID()))
				.andExpect(status().isNotFound());
	}

	@Test
	void deveRejeitarPaginaNegativaNoHistorico() throws Exception {
		mockMvc.perform(get(
					"/student/rooms/{roomId}/lessons/{lessonId}/attempts",
					UUID.randomUUID(),
					UUID.randomUUID()
			).queryParam("page", "-1"))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}

	@Test
	void deveRejeitarTamanhoDePaginaForaDoLimiteNoHistorico() throws Exception {
		mockMvc.perform(get(
					"/student/rooms/{roomId}/lessons/{lessonId}/attempts",
					UUID.randomUUID(),
					UUID.randomUUID()
			).queryParam("size", "101"))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
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
