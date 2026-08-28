package com.ifsc.contacerta.controller;

import com.ifsc.contacerta.dto.media.MediaCollectionResponse;
import com.ifsc.contacerta.model.Role;
import com.ifsc.contacerta.security.CurrentUser;
import com.ifsc.contacerta.service.StudentMediaService;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StudentMediaControllerTest {

	@Test
	void deveExporColecaoERegistroDeView() throws Exception {
		StudentMediaService service = mock(StudentMediaService.class);
		UUID studentId = UUID.randomUUID();
		UUID roomId = UUID.randomUUID();
		UUID mediaId = UUID.randomUUID();
		when(service.videos(studentId, roomId)).thenReturn(new MediaCollectionResponse<>(List.of(), 0, 0));
		var mockMvc = MockMvcBuilders.standaloneSetup(new StudentMediaController(service))
				.setCustomArgumentResolvers(resolver(new CurrentUser(studentId, Role.STUDENT, UUID.randomUUID())))
				.build();

		mockMvc.perform(get("/student/rooms/{roomId}/videos", roomId))
				.andExpect(status().isOk()).andExpect(jsonPath("$.totalCount").value(0));
		mockMvc.perform(post("/student/media/VIDEO/{mediaId}/view", mediaId))
				.andExpect(status().isNoContent());
		verify(service).registerView(studentId, com.ifsc.contacerta.model.MediaViewType.VIDEO, mediaId);
	}

	private HandlerMethodArgumentResolver resolver(CurrentUser user) {
		return new HandlerMethodArgumentResolver() {
			@Override public boolean supportsParameter(MethodParameter parameter) { return parameter.getParameterType() == CurrentUser.class; }
			@Override public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
					NativeWebRequest request, WebDataBinderFactory factory) { return user; }
		};
	}
}
