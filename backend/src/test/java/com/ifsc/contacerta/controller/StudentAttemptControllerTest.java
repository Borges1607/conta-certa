package com.ifsc.contacerta.controller;

import com.ifsc.contacerta.dto.attempt.AttemptResponse;
import com.ifsc.contacerta.dto.attempt.AttemptAnswerReceiptResponse;
import com.ifsc.contacerta.dto.attempt.AttemptAnswerReviewResponse;
import com.ifsc.contacerta.dto.attempt.AttemptAnswerValueResponse;
import com.ifsc.contacerta.dto.attempt.RecordAttemptAnswerRequest;
import com.ifsc.contacerta.dto.attempt.AttemptStartResult;
import com.ifsc.contacerta.dto.attempt.AttemptQuestionResponse;
import com.ifsc.contacerta.dto.attempt.AttemptOptionResponse;
import com.ifsc.contacerta.dto.attempt.AttemptResultResponse;
import com.ifsc.contacerta.model.AttemptStatus;
import com.ifsc.contacerta.model.QuestionType;
import com.ifsc.contacerta.model.Role;
import com.ifsc.contacerta.security.CurrentUser;
import com.ifsc.contacerta.service.AttemptService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StudentAttemptControllerTest {

	private AttemptService attemptService;
	private MockMvc mockMvc;
	private CurrentUser currentUser;

	@BeforeEach
	void setUp() {
		attemptService = mock(AttemptService.class);
		currentUser = new CurrentUser(UUID.randomUUID(), Role.STUDENT, UUID.randomUUID());
		mockMvc = MockMvcBuilders.standaloneSetup(new StudentAttemptController(attemptService))
				.setCustomArgumentResolvers(currentUserResolver(currentUser))
				.build();
	}

	@Test
	void deveRetornarAttemptIdAoIniciarTentativa() throws Exception {
		UUID assignmentId = UUID.randomUUID();
		UUID attemptId = UUID.randomUUID();
		UUID roomId = UUID.randomUUID();
		UUID lessonId = UUID.randomUUID();
		UUID snapshotId = UUID.randomUUID();
		UUID optionId = UUID.randomUUID();
		Instant startedAt = Instant.parse("2026-08-28T12:00:00Z");
		AttemptResponse response = new AttemptResponse(
				attemptId,
				assignmentId,
				roomId,
				lessonId,
				"Juros compostos",
				AttemptStatus.IN_PROGRESS,
				startedAt,
				startedAt.plusSeconds(1800),
				30,
				List.of(new AttemptQuestionResponse(
						snapshotId,
						QuestionType.SINGLE_CHOICE,
						"Quanto é 2 + 2?",
						1,
						List.of(new AttemptOptionResponse(optionId, "4")),
						null
				)),
				List.of(),
				50
		);
		URI location = URI.create("/student/attempts/" + attemptId);
		when(attemptService.start(currentUser.userId(), assignmentId, "attempt-1"))
				.thenReturn(new AttemptStartResult(HttpStatus.CREATED, location, response));

		mockMvc.perform(post("/student/room-lessons/{assignmentId}/attempts", assignmentId)
					.header("Idempotency-Key", "attempt-1"))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", location.toString()))
				.andExpect(jsonPath("$.attemptId").value(attemptId.toString()))
				.andExpect(jsonPath("$.id").doesNotExist())
				.andExpect(jsonPath("$.questions[0].questionSnapshotId").value(snapshotId.toString()))
				.andExpect(jsonPath("$.questions[0].order").value(1))
				.andExpect(jsonPath("$.questions[0].id").doesNotExist())
				.andExpect(jsonPath("$.questions[0].position").doesNotExist());
	}

	@Test
	void deveIdentificarSnapshotNoComprovanteDeResposta() throws Exception {
		UUID attemptId = UUID.randomUUID();
		UUID snapshotId = UUID.randomUUID();
		Instant answeredAt = Instant.parse("2026-08-28T12:10:00Z");
		when(attemptService.answer(
				eq(currentUser.userId()),
				eq(attemptId),
				eq(snapshotId),
				any(RecordAttemptAnswerRequest.class)
		)).thenReturn(new AttemptAnswerReceiptResponse(snapshotId, answeredAt, true));

		mockMvc.perform(put("/student/attempts/{attemptId}/answers/{snapshotId}", attemptId, snapshotId)
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"booleanValue\":true}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.questionSnapshotId").value(snapshotId.toString()))
				.andExpect(jsonPath("$.correct").value(true))
				.andExpect(jsonPath("$.answeredAt").value("2026-08-28T12:10:00Z"));
	}

	@Test
	void deveExporResultadoNoContratoConsumidoPeloFrontend() throws Exception {
		UUID attemptId = UUID.randomUUID();
		UUID assignmentId = UUID.randomUUID();
		UUID roomId = UUID.randomUUID();
		UUID lessonId = UUID.randomUUID();
		UUID snapshotId = UUID.randomUUID();
		Instant startedAt = Instant.parse("2026-08-28T12:00:00Z");
		Instant submittedAt = Instant.parse("2026-08-28T12:20:00Z");
		AttemptAnswerReviewResponse answer = new AttemptAnswerReviewResponse(
				new AttemptQuestionResponse(
						snapshotId,
						QuestionType.TRUE_FALSE,
						"A taxa é de 10%?",
						1,
						List.of(),
						null
				),
				new AttemptAnswerValueResponse(null, true, null),
				new AttemptAnswerValueResponse(null, true, null),
				true,
				"A taxa informada no enunciado é 10%."
		);
		AttemptResultResponse response = new AttemptResultResponse(
				attemptId,
				assignmentId,
				roomId,
				lessonId,
				"Juros compostos",
				AttemptStatus.SUBMITTED,
				7,
				10,
				70,
				true,
				2,
				20,
				150,
				startedAt,
				submittedAt,
				50,
				2L,
				List.of(answer)
		);
		when(attemptService.result(currentUser.userId(), attemptId)).thenReturn(response);

		mockMvc.perform(get("/student/attempts/{attemptId}/result", attemptId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.attemptId").value(attemptId.toString()))
				.andExpect(jsonPath("$.xpEarnedThisAttempt").value(20))
				.andExpect(jsonPath("$.roomXpTotal").isNumber())
				.andExpect(jsonPath("$.startedAt").isString())
				.andExpect(jsonPath("$.answers").isArray())
				.andExpect(jsonPath("$.answers[0].question.questionSnapshotId").value(snapshotId.toString()))
				.andExpect(jsonPath("$.answers[0].studentAnswer.booleanValue").value(true))
				.andExpect(jsonPath("$.answers[0].correctAnswer.booleanValue").value(true))
				.andExpect(jsonPath("$.answers[0].explanation").isString())
				.andExpect(jsonPath("$.id").doesNotExist())
				.andExpect(jsonPath("$.xpCredited").doesNotExist())
				.andExpect(jsonPath("$.review").doesNotExist());
	}

	@Test
	void deveExporContextoCompletoAoRetomarTentativa() throws Exception {
		UUID attemptId = UUID.randomUUID();
		UUID assignmentId = UUID.randomUUID();
		UUID roomId = UUID.randomUUID();
		UUID lessonId = UUID.randomUUID();
		Instant startedAt = Instant.parse("2026-08-28T12:00:00Z");
		AttemptResponse response = new AttemptResponse(
				attemptId,
				assignmentId,
				roomId,
				lessonId,
				"Juros compostos",
				AttemptStatus.IN_PROGRESS,
				startedAt,
				startedAt.plusSeconds(1800),
				30,
				List.of(),
				List.of(),
				50
		);
		when(attemptService.get(currentUser.userId(), attemptId)).thenReturn(response);

		mockMvc.perform(get("/student/attempts/{attemptId}", attemptId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.attemptId").value(attemptId.toString()))
				.andExpect(jsonPath("$.assignmentId").value(assignmentId.toString()))
				.andExpect(jsonPath("$.roomId").isString())
				.andExpect(jsonPath("$.lessonId").isString())
				.andExpect(jsonPath("$.lessonTitle").isString())
				.andExpect(jsonPath("$.timeLimitMinutes").isNumber())
				.andExpect(jsonPath("$.answers").isArray())
				.andExpect(jsonPath("$.passingScorePercent").isNumber());
	}

	private HandlerMethodArgumentResolver currentUserResolver(CurrentUser user) {
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
				return user;
			}
		};
	}
}
