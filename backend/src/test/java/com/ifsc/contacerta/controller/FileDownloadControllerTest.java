package com.ifsc.contacerta.controller;

import com.ifsc.contacerta.exception.ApiException;
import com.ifsc.contacerta.exception.GlobalExceptionHandler;
import com.ifsc.contacerta.mapper.FileDownloadResponseMapper;
import com.ifsc.contacerta.model.FileDownload;
import com.ifsc.contacerta.model.Role;
import com.ifsc.contacerta.security.CurrentUser;
import com.ifsc.contacerta.service.FileDownloadService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FileDownloadControllerTest {

	private FileDownloadService service;
	private MockMvc mockMvc;
	private final UUID userId = UUID.randomUUID();
	private final UUID fileId = UUID.randomUUID();

	@BeforeEach
	void setUp() {
		service = mock(FileDownloadService.class);
		SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
				new CurrentUser(userId, Role.TEACHER, UUID.randomUUID()), null, List.of()));
		mockMvc = MockMvcBuilders.standaloneSetup(new FileDownloadController(service, new FileDownloadResponseMapper()))
				.setControllerAdvice(new GlobalExceptionHandler(Clock.systemUTC()))
				.setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver()).build();
	}

	@AfterEach
	void clearContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void deveRemoverNulEControlesDeNomeSemAlterarBytesDaResposta() throws Exception {
		when(service.get(userId, fileId)).thenReturn(new FileDownload(
				"/pasta/ lição\u0000\u0085.pdf ", "application/pdf", 4, new byte[]{1, 2, 3, 4}));
		var response = mockMvc.perform(get("/files/{id}/download", fileId).header("Range", "bytes=1-2"))
				.andExpect(status().isOk()).andExpect(content().bytes(new byte[]{1, 2, 3, 4}))
				.andExpect(header().string("Content-Length", "4"))
				.andExpect(header().string("Cache-Control", "private, no-store"))
				.andExpect(header().string("X-Content-Type-Options", "nosniff"))
				.andExpect(header().doesNotExist("Content-Range"))
				.andExpect(header().doesNotExist("Accept-Ranges")).andReturn().getResponse();
		assertThat(ContentDisposition.parse(response.getHeader("Content-Disposition")).getFilename()).isEqualTo("lição.pdf");
	}

	@Test
	void deveRejeitarUuidInvalidoAntesDoServico() throws Exception {
		mockMvc.perform(get("/files/1-1-1-1-1/download"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
		verifyNoInteractions(service);
	}

	@Test
	void devePreservarErroDoServicoNoEnvelopeDaApi() throws Exception {
		when(service.get(userId, fileId)).thenThrow(new ApiException(HttpStatus.NOT_FOUND, "FILE_NOT_FOUND", "File was not found."));
		mockMvc.perform(get("/files/{id}/download", fileId)).andExpect(status().isNotFound())
				.andExpect(content().contentType("application/problem+json"))
				.andExpect(jsonPath("$.code").value("FILE_NOT_FOUND"));
	}
}
