package com.ifsc.contacerta.controller;

import com.ifsc.contacerta.dto.report.ReportScoreDistributionResponse;
import com.ifsc.contacerta.dto.report.ReportPeriod;
import com.ifsc.contacerta.dto.report.TeacherReportOverviewResponse;
import com.ifsc.contacerta.dto.report.TeacherReportRankingResponse;
import com.ifsc.contacerta.model.Role;
import com.ifsc.contacerta.security.CurrentUser;
import com.ifsc.contacerta.service.TeacherReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TeacherReportControllerTest {

	private TeacherReportService service;
	private MockMvc mockMvc;
	private CurrentUser currentUser;

	@BeforeEach
	void setUp() {
		service = mock(TeacherReportService.class);
		currentUser = new CurrentUser(UUID.randomUUID(), Role.TEACHER, UUID.randomUUID());
		mockMvc = MockMvcBuilders.standaloneSetup(new TeacherReportController(service))
				.setCustomArgumentResolvers(currentUserResolver())
				.build();
	}

	@Test
	void deveExporOverviewComPeriodoAll() throws Exception {
		UUID roomId = UUID.randomUUID();
		TeacherReportOverviewResponse response = new TeacherReportOverviewResponse(
				2, 1, new BigDecimal("75.00"), new BigDecimal("25.00"), new BigDecimal("3.00"),
				List.of(), new ReportScoreDistributionResponse(1, 1, 1, 1), List.of()
		);
		when(service.overview(currentUser.userId(), roomId, null,
				ReportPeriod.ALL, null, null)).thenReturn(response);

		mockMvc.perform(get("/teacher/reports/overview").param("roomId", roomId.toString()).param("period", "ALL"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.activeStudentCount").value(2))
				.andExpect(jsonPath("$.scoreDistribution.score90To100").value(1));
	}

	@Test
	void deveExporAlunosComDefaultsDePaginacao() throws Exception {
		UUID roomId = UUID.randomUUID();
		PageRequest pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "totalXp"));
		when(service.students(currentUser.userId(), roomId, null, null, null, null,
				0, 20, "totalXp", "desc")).thenReturn(new PageImpl<>(List.of(), pageable, 0));

		mockMvc.perform(get("/teacher/reports/students").param("roomId", roomId.toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").isArray())
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(20));
	}

	@Test
	void deveExporTentativasDoAluno() throws Exception {
		UUID roomId = UUID.randomUUID();
		UUID studentId = UUID.randomUUID();
		PageRequest pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "submittedAt"));
		when(service.attempts(currentUser.userId(), roomId, studentId, null, null, null, null,
				0, 20, "desc")).thenReturn(new PageImpl<>(List.of(), pageable, 0));

		mockMvc.perform(get("/teacher/reports/students/{studentId}/attempts", studentId)
					.param("roomId", roomId.toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").isArray());
	}

	@Test
	void deveExporRankingCompleto() throws Exception {
		UUID roomId = UUID.randomUUID();
		UUID studentId = UUID.randomUUID();
		when(service.ranking(currentUser.userId(), roomId, null, null, null, null)).thenReturn(List.of(
				new TeacherReportRankingResponse(1, studentId, "Aluno", "S1", "a@example.com", 10, 2, null)
		));

		mockMvc.perform(get("/teacher/reports/ranking").param("roomId", roomId.toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].position").value(1))
				.andExpect(jsonPath("$[0].studentId").value(studentId.toString()));
	}

	private HandlerMethodArgumentResolver currentUserResolver() {
		return new HandlerMethodArgumentResolver() {
			@Override
			public boolean supportsParameter(MethodParameter parameter) {
				return parameter.getParameterType().equals(CurrentUser.class);
			}

			@Override
			public Object resolveArgument(
					MethodParameter parameter,
					ModelAndViewContainer container,
					NativeWebRequest request,
					WebDataBinderFactory binderFactory
			) {
				return currentUser;
			}
		};
	}
}
