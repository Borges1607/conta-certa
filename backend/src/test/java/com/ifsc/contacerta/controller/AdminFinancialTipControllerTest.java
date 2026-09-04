package com.ifsc.contacerta.controller;

import com.ifsc.contacerta.dto.admin.AdminFinancialTipResponse;
import com.ifsc.contacerta.dto.admin.CreateFinancialTipRequest;
import com.ifsc.contacerta.service.AdminFinancialTipService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminFinancialTipControllerTest {

	private AdminFinancialTipService service;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		service = mock(AdminFinancialTipService.class);
		mockMvc = MockMvcBuilders.standaloneSetup(new AdminFinancialTipController(service)).build();
	}

	@Test
	void criaDicaComLocation() throws Exception {
		UUID id = UUID.randomUUID();
		when(service.create(any(CreateFinancialTipRequest.class))).thenReturn(response(id));

		mockMvc.perform(post("/admin/financial-tips")
				.contentType("application/json")
				.content("{\"title\":\"Reserva\",\"content\":\"**Poupe**\",\"sourceUrl\":\"https://example.org\",\"publicationDate\":\"2026-09-03\"}"))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", "/admin/financial-tips/" + id))
				.andExpect(jsonPath("$.publicationDate").value("2026-09-03"));
	}

	@Test
	void listaDicasComFiltrosIncluindoDataLocal() throws Exception {
		when(service.list(eq("reserva"), eq(true), eq(LocalDate.of(2026, 9, 3)), any()))
				.thenReturn(new PageImpl<>(List.of(response(UUID.randomUUID())), PageRequest.of(0, 20), 1));

		mockMvc.perform(get("/admin/financial-tips")
				.param("search", "reserva")
				.param("active", "true")
				.param("publicationDate", "2026-09-03"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").isArray())
				.andExpect(jsonPath("$.content[0].publicationDate").value("2026-09-03"))
				.andExpect(jsonPath("$.totalElements").value(1));

		verify(service).list(eq("reserva"), eq(true), eq(LocalDate.of(2026, 9, 3)), any());
	}

	@Test
	void arquivaDicaEAtivaPorAcoesExplicitas() throws Exception {
		UUID id = UUID.randomUUID();

		mockMvc.perform(delete("/admin/financial-tips/{id}", id))
				.andExpect(status().isNoContent());
		mockMvc.perform(post("/admin/financial-tips/{id}/activate", id))
				.andExpect(status().isOk());
		mockMvc.perform(post("/admin/financial-tips/{id}/deactivate", id))
				.andExpect(status().isOk());

		verify(service).archive(id);
		verify(service).activate(id);
		verify(service).deactivate(id);
	}

	private AdminFinancialTipResponse response(UUID id) {
		return new AdminFinancialTipResponse(id, "Reserva", "**Poupe**", "https://example.org",
				LocalDate.of(2026, 9, 3), true, null, null, 0, null);
	}
}
