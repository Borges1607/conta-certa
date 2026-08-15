package com.ifsc.contacerta.controller;

import com.ifsc.contacerta.dto.institution.InstitutionOptionResponse;
import com.ifsc.contacerta.service.InstitutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class InstitutionOptionControllerTest {

	@Mock
	private InstitutionService institutionService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders
				.standaloneSetup(new InstitutionOptionController(institutionService))
				.build();
	}

	@Test
	void deveListarOpcoesPublicasDeInstituicoesAtivas() throws Exception {
		UUID id = UUID.fromString("4f24e780-31a8-4a3c-a612-5f4984ea5ab1");
		when(institutionService.listActiveOptions()).thenReturn(List.of(
				new InstitutionOptionResponse(id, "Instituto Exemplo", "11222333000181")
		));

		mockMvc.perform(get("/institutions/options"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value(id.toString()))
				.andExpect(jsonPath("$[0].name").value("Instituto Exemplo"))
				.andExpect(jsonPath("$[0].cnpj").value("11222333000181"));
	}
}
