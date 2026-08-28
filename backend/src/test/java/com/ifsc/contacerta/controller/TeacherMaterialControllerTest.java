package com.ifsc.contacerta.controller;

import com.ifsc.contacerta.dto.material.TeacherMaterialResponse;
import com.ifsc.contacerta.dto.shared.PageResponse;
import com.ifsc.contacerta.model.ContentStatus;
import com.ifsc.contacerta.model.MaterialKind;
import com.ifsc.contacerta.model.Role;
import com.ifsc.contacerta.security.CurrentUser;
import com.ifsc.contacerta.service.MaterialService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.Instant;
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

class TeacherMaterialControllerTest {

	private MaterialService service;
	private MockMvc mockMvc;
	private UUID teacherId;

	@BeforeEach
	void setUp() {
		service = mock(MaterialService.class);
		teacherId = UUID.randomUUID();
		mockMvc = MockMvcBuilders.standaloneSetup(new TeacherMaterialController(service))
				.setCustomArgumentResolvers(currentUserResolver(new CurrentUser(teacherId, Role.TEACHER, UUID.randomUUID())))
				.build();
	}

	@Test
	void deveListarComBuscaKindPaginacaoEOrdenacao() throws Exception {
		TeacherMaterialResponse material = response();
		when(service.list(eq(teacherId), eq("apostila"), eq(MaterialKind.FILE), any()))
				.thenReturn(new PageResponse<>(List.of(material), 0, 10, 1, 1));

		mockMvc.perform(get("/teacher/materials")
					.param("search", "apostila").param("kind", "FILE")
					.param("page", "0").param("size", "10").param("sort", "title,asc"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].id").value(material.id().toString()));
	}

	@Test
	void deveCriarMaterialComLocation() throws Exception {
		TeacherMaterialResponse material = response();
		when(service.create(eq(teacherId), any())).thenReturn(material);

		mockMvc.perform(post("/teacher/materials")
					.contentType("application/json")
					.content("""
							{"title":"Apostila","description":null,"category":"Finanças","kind":"FILE","url":null,"fileId":"%s"}
							""".formatted(UUID.randomUUID())))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", "/teacher/materials/" + material.id()))
				.andExpect(jsonPath("$.title").value("Apostila"));
	}

	@Test
	void deveArquivarMaterial() throws Exception {
		UUID materialId = UUID.randomUUID();
		mockMvc.perform(delete("/teacher/materials/{materialId}", materialId)).andExpect(status().isNoContent());
		verify(service).archive(teacherId, materialId);
	}

	private TeacherMaterialResponse response() {
		Instant now = Instant.parse("2026-08-28T12:00:00Z");
		return new TeacherMaterialResponse(
				UUID.randomUUID(), "Apostila", null, "Finanças", MaterialKind.FILE, null, null,
				ContentStatus.PUBLISHED, now, now, 0
		);
	}

	private HandlerMethodArgumentResolver currentUserResolver(CurrentUser currentUser) {
		return new HandlerMethodArgumentResolver() {
			@Override
			public boolean supportsParameter(MethodParameter parameter) {
				return parameter.getParameterType() == CurrentUser.class;
			}

			@Override
			public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
					NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
				return currentUser;
			}
		};
	}
}
