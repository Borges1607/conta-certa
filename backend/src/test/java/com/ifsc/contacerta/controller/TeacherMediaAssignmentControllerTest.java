package com.ifsc.contacerta.controller;

import com.ifsc.contacerta.dto.media.MediaAssignmentResponse;
import com.ifsc.contacerta.model.MediaViewType;
import com.ifsc.contacerta.model.Role;
import com.ifsc.contacerta.security.CurrentUser;
import com.ifsc.contacerta.service.MediaAssignmentService;
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

class TeacherMediaAssignmentControllerTest {

	private MediaAssignmentService service;
	private MockMvc mockMvc;
	private UUID teacherId;
	private UUID roomId;

	@BeforeEach
	void setUp() {
		service = mock(MediaAssignmentService.class);
		teacherId = UUID.randomUUID();
		roomId = UUID.randomUUID();
		mockMvc = MockMvcBuilders.standaloneSetup(new TeacherMediaAssignmentController(service))
				.setCustomArgumentResolvers(currentUserResolver(new CurrentUser(teacherId, Role.TEACHER, UUID.randomUUID())))
				.build();
	}

	@Test
	void deveListarVinculosDaSala() throws Exception {
		when(service.list(teacherId, roomId)).thenReturn(List.of(response()));
		mockMvc.perform(get("/teacher/rooms/{roomId}/media-assignments", roomId))
				.andExpect(status().isOk()).andExpect(jsonPath("$[0].position").value(1));
	}

	@Test
	void deveCriarComLocation() throws Exception {
		MediaAssignmentResponse response = response();
		when(service.create(eq(teacherId), eq(roomId), any())).thenReturn(response);
		mockMvc.perform(post("/teacher/rooms/{roomId}/media-assignments", roomId)
					.contentType("application/json")
					.content("""
							{"mediaType":"VIDEO","mediaId":"%s","lessonAssignmentId":null}
							""".formatted(response.mediaId())))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", "/teacher/rooms/" + roomId + "/media-assignments/" + response.id()));
	}

	@Test
	void deveRemoverVinculo() throws Exception {
		UUID assignmentId = UUID.randomUUID();
		mockMvc.perform(delete("/teacher/rooms/{roomId}/media-assignments/{assignmentId}", roomId, assignmentId))
				.andExpect(status().isNoContent());
		verify(service).delete(teacherId, roomId, assignmentId);
	}

	private MediaAssignmentResponse response() {
		return new MediaAssignmentResponse(UUID.randomUUID(), roomId, MediaViewType.VIDEO, UUID.randomUUID(),
				"Juros", null, null, 1, Instant.parse("2026-08-28T12:00:00Z"), 0);
	}

	private HandlerMethodArgumentResolver currentUserResolver(CurrentUser currentUser) {
		return new HandlerMethodArgumentResolver() {
			@Override public boolean supportsParameter(MethodParameter parameter) {
				return parameter.getParameterType() == CurrentUser.class;
			}
			@Override public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
					NativeWebRequest request, WebDataBinderFactory factory) {
				return currentUser;
			}
		};
	}
}
