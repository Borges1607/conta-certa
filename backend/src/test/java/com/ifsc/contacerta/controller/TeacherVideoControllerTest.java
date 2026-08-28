package com.ifsc.contacerta.controller;

import com.ifsc.contacerta.dto.shared.PageResponse;
import com.ifsc.contacerta.dto.video.TeacherVideoResponse;
import com.ifsc.contacerta.model.ContentStatus;
import com.ifsc.contacerta.model.Role;
import com.ifsc.contacerta.security.CurrentUser;
import com.ifsc.contacerta.service.VideoService;
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

class TeacherVideoControllerTest {

	private VideoService service;
	private MockMvc mockMvc;
	private UUID teacherId;

	@BeforeEach
	void setUp() {
		service = mock(VideoService.class);
		teacherId = UUID.randomUUID();
		mockMvc = MockMvcBuilders.standaloneSetup(new TeacherVideoController(service))
				.setCustomArgumentResolvers(currentUserResolver(new CurrentUser(teacherId, Role.TEACHER, UUID.randomUUID())))
				.build();
	}

	@Test
	void deveListarComBuscaCategoriaPaginacaoEOrdenacao() throws Exception {
		TeacherVideoResponse video = response();
		when(service.list(eq(teacherId), eq("juros"), eq("Finanças"), any()))
				.thenReturn(new PageResponse<>(List.of(video), 1, 10, 1, 1));

		mockMvc.perform(get("/teacher/videos")
					.param("search", "juros")
					.param("category", "Finanças")
					.param("page", "1")
					.param("size", "10")
					.param("sort", "title,asc"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].id").value(video.id().toString()))
				.andExpect(jsonPath("$.page").value(1));
	}

	@Test
	void deveCriarVideoComLocation() throws Exception {
		TeacherVideoResponse video = response();
		when(service.create(eq(teacherId), any())).thenReturn(video);

		mockMvc.perform(post("/teacher/videos")
					.contentType("application/json")
					.content("""
							{"title":"Juros","description":null,"category":"Finanças","url":"https://example.com/video"}
							"""))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", "/teacher/videos/" + video.id()))
				.andExpect(jsonPath("$.title").value("Juros"));
	}

	@Test
	void deveArquivarVideo() throws Exception {
		UUID videoId = UUID.randomUUID();

		mockMvc.perform(delete("/teacher/videos/{videoId}", videoId))
				.andExpect(status().isNoContent());

		verify(service).archive(teacherId, videoId);
	}

	private TeacherVideoResponse response() {
		Instant now = Instant.parse("2026-08-28T12:00:00Z");
		return new TeacherVideoResponse(
				UUID.randomUUID(), "Juros", null, "Finanças", "https://example.com/video",
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
