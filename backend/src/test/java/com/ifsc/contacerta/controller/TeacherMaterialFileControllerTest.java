package com.ifsc.contacerta.controller;

import com.ifsc.contacerta.dto.material.MaterialFileResponse;
import com.ifsc.contacerta.model.Role;
import com.ifsc.contacerta.security.CurrentUser;
import com.ifsc.contacerta.service.MaterialFileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TeacherMaterialFileControllerTest {

	private MaterialFileService service;
	private MockMvc mockMvc;
	private UUID teacherId;

	@BeforeEach
	void setUp() {
		service = mock(MaterialFileService.class);
		teacherId = UUID.randomUUID();
		mockMvc = MockMvcBuilders.standaloneSetup(new TeacherMaterialFileController(service))
				.setCustomArgumentResolvers(currentUserResolver(new CurrentUser(teacherId, Role.TEACHER, UUID.randomUUID())))
				.build();
	}

	@Test
	void deveReceberMultipartERetornarArquivoCriado() throws Exception {
		UUID fileId = UUID.randomUUID();
		when(service.upload(org.mockito.ArgumentMatchers.eq(teacherId), any()))
				.thenReturn(new MaterialFileResponse(fileId, "aula.pdf", "application/pdf", 12));
		MockMultipartFile file = new MockMultipartFile(
				"file", "aula.pdf", "application/pdf", "%PDF-1.7\n".getBytes()
		);

		mockMvc.perform(multipart("/teacher/materials/files").file(file))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", "/files/" + fileId))
				.andExpect(jsonPath("$.id").value(fileId.toString()))
				.andExpect(jsonPath("$.fileName").value("aula.pdf"))
				.andExpect(jsonPath("$.contentType").value("application/pdf"));
	}

	private HandlerMethodArgumentResolver currentUserResolver(CurrentUser currentUser) {
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
				return currentUser;
			}
		};
	}
}
