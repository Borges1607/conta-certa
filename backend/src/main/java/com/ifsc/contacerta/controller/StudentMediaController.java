package com.ifsc.contacerta.controller;

import com.ifsc.contacerta.dto.media.MediaCollectionResponse;
import com.ifsc.contacerta.dto.media.StudentMaterialResponse;
import com.ifsc.contacerta.dto.media.StudentVideoResponse;
import com.ifsc.contacerta.model.MediaViewType;
import com.ifsc.contacerta.security.CurrentUser;
import com.ifsc.contacerta.service.StudentMediaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/student")
@RequiredArgsConstructor
public class StudentMediaController {

	private final StudentMediaService mediaService;

	@GetMapping("/rooms/{roomId}/videos")
	public MediaCollectionResponse<StudentVideoResponse> videos(
			@AuthenticationPrincipal CurrentUser currentUser, @PathVariable UUID roomId
	) {
		return mediaService.videos(currentUser.userId(), roomId);
	}

	@GetMapping("/rooms/{roomId}/materials")
	public MediaCollectionResponse<StudentMaterialResponse> materials(
			@AuthenticationPrincipal CurrentUser currentUser, @PathVariable UUID roomId
	) {
		return mediaService.materials(currentUser.userId(), roomId);
	}

	@PostMapping("/media/{mediaType}/{mediaId}/view")
	public ResponseEntity<Void> registerView(
			@AuthenticationPrincipal CurrentUser currentUser,
			@PathVariable MediaViewType mediaType,
			@PathVariable UUID mediaId
	) {
		mediaService.registerView(currentUser.userId(), mediaType, mediaId);
		return ResponseEntity.noContent().build();
	}
}
