package com.ifsc.contacerta.controller;

import com.ifsc.contacerta.dto.gamification.RankingEntryResponse;
import com.ifsc.contacerta.dto.studentdashboard.StudentDashboardProgressResponse;
import com.ifsc.contacerta.dto.studentdashboard.StudentRoomDashboardResponse;
import com.ifsc.contacerta.model.Role;
import com.ifsc.contacerta.security.CurrentUser;
import com.ifsc.contacerta.service.RoomMembershipService;
import com.ifsc.contacerta.service.StudentRoomDashboardService;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StudentRoomDashboardControllerTest {

	@Test
	void deveExporDashboardDaSalaParaAlunoAutenticado() throws Exception {
		RoomMembershipService membershipService = mock(RoomMembershipService.class);
		StudentRoomDashboardService dashboardService = mock(StudentRoomDashboardService.class);
		UUID studentId = UUID.randomUUID();
		UUID roomId = UUID.randomUUID();
		when(dashboardService.dashboard(studentId, roomId)).thenReturn(new StudentRoomDashboardResponse(
				null,
				new StudentDashboardProgressResponse(150, 2, 50, 5, 3, 2, 4),
				null,
				List.of(),
				null,
				new RankingEntryResponse(3, studentId, "Aluno S.", 150, 5, 2, true)
		));
		var mockMvc = MockMvcBuilders.standaloneSetup(new StudentRoomController(membershipService, dashboardService))
				.setCustomArgumentResolvers(resolver(new CurrentUser(studentId, Role.STUDENT, UUID.randomUUID())))
				.build();

		mockMvc.perform(get("/student/rooms/{roomId}/dashboard", roomId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.progress.totalXp").value(150))
				.andExpect(jsonPath("$.ranking.position").value(3))
				.andExpect(jsonPath("$.recentAchievements").isArray());
		verify(dashboardService).dashboard(studentId, roomId);
	}

	private HandlerMethodArgumentResolver resolver(CurrentUser user) {
		return new HandlerMethodArgumentResolver() {
			@Override public boolean supportsParameter(MethodParameter parameter) {
				return parameter.getParameterType() == CurrentUser.class;
			}
			@Override public Object resolveArgument(
					MethodParameter parameter,
					ModelAndViewContainer container,
					NativeWebRequest request,
					WebDataBinderFactory factory
			) {
				return user;
			}
		};
	}
}
