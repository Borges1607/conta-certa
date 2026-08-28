package com.ifsc.contacerta.controller;

import com.ifsc.contacerta.dto.media.CreateMediaAssignmentRequest;
import com.ifsc.contacerta.dto.media.MediaAssignmentResponse;
import com.ifsc.contacerta.dto.media.PatchMediaAssignmentRequest;
import com.ifsc.contacerta.security.CurrentUser;
import com.ifsc.contacerta.service.MediaAssignmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/teacher/rooms/{roomId}/media-assignments")
@RequiredArgsConstructor
public class TeacherMediaAssignmentController {

	private final MediaAssignmentService assignmentService;

	@GetMapping
	public List<MediaAssignmentResponse> list(
			@AuthenticationPrincipal CurrentUser currentUser, @PathVariable UUID roomId
	) {
		return assignmentService.list(currentUser.userId(), roomId);
	}

	@PostMapping
	public ResponseEntity<MediaAssignmentResponse> create(
			@AuthenticationPrincipal CurrentUser currentUser,
			@PathVariable UUID roomId,
			@Valid @RequestBody CreateMediaAssignmentRequest request
	) {
		MediaAssignmentResponse response = assignmentService.create(currentUser.userId(), roomId, request);
		return ResponseEntity.created(URI.create(
				"/teacher/rooms/" + roomId + "/media-assignments/" + response.id()
		)).body(response);
	}

	@PatchMapping("/{assignmentId}")
	public MediaAssignmentResponse update(
			@AuthenticationPrincipal CurrentUser currentUser,
			@PathVariable UUID roomId,
			@PathVariable UUID assignmentId,
			@Valid @RequestBody PatchMediaAssignmentRequest request
	) {
		return assignmentService.update(currentUser.userId(), roomId, assignmentId, request);
	}

	@DeleteMapping("/{assignmentId}")
	public ResponseEntity<Void> delete(
			@AuthenticationPrincipal CurrentUser currentUser,
			@PathVariable UUID roomId,
			@PathVariable UUID assignmentId
	) {
		assignmentService.delete(currentUser.userId(), roomId, assignmentId);
		return ResponseEntity.noContent().build();
	}
}
