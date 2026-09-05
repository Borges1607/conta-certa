package com.ifsc.contacerta.controller;

import com.ifsc.contacerta.dto.teacher.TeacherDashboardResponse;
import com.ifsc.contacerta.model.Role;
import com.ifsc.contacerta.security.CurrentUser;
import com.ifsc.contacerta.service.TeacherDashboardService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TeacherDashboardControllerTest {

	private TeacherDashboardService service;
	private MockMvc mockMvc;
	private CurrentUser currentUser;

	@BeforeEach
	void setUp() {
		service = mock(TeacherDashboardService.class);
		currentUser = new CurrentUser(UUID.randomUUID(), Role.TEACHER, UUID.randomUUID());
		SecurityContextHolder.getContext().setAuthentication(
				new UsernamePasswordAuthenticationToken(currentUser, null, List.of())
		);
		mockMvc = MockMvcBuilders.standaloneSetup(new TeacherDashboardController(service))
				.setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
				.build();
	}

	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void deveExporEnvelopeCompletoUsandoIdDoProfessorAutenticado() throws Exception {
		when(service.get(currentUser.userId())).thenReturn(new TeacherDashboardResponse(
				new TeacherDashboardResponse.RoomCounts(4, 3, 1),
				new TeacherDashboardResponse.StudentCounts(86, 80),
				new TeacherDashboardResponse.LessonCounts(12, 9, 3),
				new TeacherDashboardResponse.AssignmentCounts(24, 20)
		));

		mockMvc.perform(get("/teacher/dashboard"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(content().json("""
						{
						  "rooms": {"total": 4, "active": 3, "archived": 1},
						  "students": {"total": 86, "activeMemberships": 80},
						  "lessons": {"total": 12, "published": 9, "draft": 3},
						  "assignments": {"total": 24, "published": 20}
						}
						""", JsonCompareMode.STRICT));
	}
}
