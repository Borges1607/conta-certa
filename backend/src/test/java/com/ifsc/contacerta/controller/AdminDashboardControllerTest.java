package com.ifsc.contacerta.controller;

import com.ifsc.contacerta.dto.admin.AdminDashboardResponse;
import com.ifsc.contacerta.service.AdminDashboardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminDashboardControllerTest {
	private AdminDashboardService service;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		service = mock(AdminDashboardService.class);
		mockMvc = MockMvcBuilders.standaloneSetup(new AdminDashboardController(service)).build();
	}

	@Test
	void retornaSomenteAgregadosDoDashboard() throws Exception {
		when(service.get()).thenReturn(new AdminDashboardResponse(
				new AdminDashboardResponse.InstitutionCounts(10, 8, 2),
				new AdminDashboardResponse.TeacherCounts(25, 3, 20, 2)));

		mockMvc.perform(get("/admin/dashboard"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.institutions.total").value(10))
				.andExpect(jsonPath("$.institutions.active").value(8))
				.andExpect(jsonPath("$.teachers.pending").value(3))
				.andExpect(jsonPath("$.rooms").doesNotExist())
				.andExpect(jsonPath("$.content").doesNotExist());
	}
}
