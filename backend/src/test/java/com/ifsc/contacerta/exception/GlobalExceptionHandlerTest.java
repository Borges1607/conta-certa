package com.ifsc.contacerta.exception;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		Clock clock = Clock.fixed(Instant.parse("2026-08-15T20:00:00Z"), ZoneOffset.UTC);
		mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
				.setControllerAdvice(new GlobalExceptionHandler(clock))
				.build();
	}

	@Test
	void deveRetornarProblemDetailsParaErroDeNegocio() throws Exception {
		mockMvc.perform(get("/test/conflict").header("X-Trace-Id", "trace-123"))
				.andExpect(status().isConflict())
				.andExpect(content().contentType("application/problem+json"))
				.andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"))
				.andExpect(jsonPath("$.detail").value("Email is already registered."))
				.andExpect(jsonPath("$.timestamp").value("2026-08-15T20:00:00Z"))
				.andExpect(jsonPath("$.traceId").value("trace-123"));
	}

	@Test
	void deveRetornarErrosDeCampoParaEntradaInvalida() throws Exception {
		mockMvc.perform(post("/test/validation")
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"name\":\"\"}"))
				.andExpect(status().is(422))
				.andExpect(content().contentType("application/problem+json"))
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("name"))
				.andExpect(jsonPath("$.fieldErrors[0].message").value("must not be blank"));
	}

	@RestController
	static class TestController {

		@GetMapping("/test/conflict")
		void conflict() {
			throw new ApiException(
					HttpStatus.CONFLICT,
					"EMAIL_ALREADY_EXISTS",
					"Email is already registered."
			);
		}

		@PostMapping("/test/validation")
		void validation(@Valid @RequestBody TestRequest request) {
		}
	}

	record TestRequest(@NotBlank String name) {
	}
}
