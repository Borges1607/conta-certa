package com.ifsc.contacerta.controller;

import com.ifsc.contacerta.dto.admin.AdminInstitutionResponse;
import com.ifsc.contacerta.dto.institution.CreateInstitutionRequest;
import com.ifsc.contacerta.service.AdminInstitutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminInstitutionControllerTest {

	private AdminInstitutionService service;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		service = mock(AdminInstitutionService.class);
		mockMvc = MockMvcBuilders.standaloneSetup(new AdminInstitutionController(service)).build();
	}

	@Test
	void criaInstituicaoComLocation() throws Exception {
		UUID id = UUID.randomUUID();
		when(service.create(any(CreateInstitutionRequest.class))).thenReturn(response(id));

		mockMvc.perform(post("/admin/institutions")
				.contentType("application/json")
				.content("{\"name\":\"Instituto Alfa\",\"cnpj\":\"12345678000195\",\"contactEmail\":\"a@example.com\",\"contactPhone\":\"+5548999999999\"}"))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", "/admin/institutions/" + id))
				.andExpect(jsonPath("$.cnpj").value("12345678000195"));
	}

	@Test
	void listaInstituicoesComEnvelopePaginado() throws Exception {
		when(service.list(eq("Alfa"), eq(true), any())).thenReturn(new PageImpl<>(List.of(response(UUID.randomUUID())), PageRequest.of(1, 20), 21));

		mockMvc.perform(get("/admin/institutions")
				.param("search", "Alfa")
				.param("active", "true")
				.param("page", "1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").isArray())
				.andExpect(jsonPath("$.page").value(1))
				.andExpect(jsonPath("$.totalElements").value(21));

		verify(service).list(eq("Alfa"), eq(true), any());
	}

	private AdminInstitutionResponse response(UUID id) {
		return new AdminInstitutionResponse(id, "Instituto Alfa", "12345678000195", "a@example.com", "+5548999999999", true, null, null, 0, 1, 2);
	}
}
