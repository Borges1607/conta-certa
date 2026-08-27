package com.ifsc.contacerta.controller;

import com.ifsc.contacerta.dto.assignment.CreateLessonAssignmentRequest;
import com.ifsc.contacerta.dto.assignment.LessonAssignmentOrderRequest;
import com.ifsc.contacerta.dto.assignment.LessonAssignmentResponse;
import com.ifsc.contacerta.dto.assignment.UpdateLessonAssignmentRequest;
import com.ifsc.contacerta.security.CurrentUser;
import com.ifsc.contacerta.service.LessonAssignmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/teacher/rooms/{roomId}/lesson-assignments")
@RequiredArgsConstructor
public class TeacherLessonAssignmentController {

	private final LessonAssignmentService assignmentService;

	@GetMapping
	public List<LessonAssignmentResponse> list(
			@AuthenticationPrincipal CurrentUser currentUser,
			@PathVariable UUID roomId
	) {
		return assignmentService.list(currentUser.userId(), roomId);
	}

	@PostMapping
	public ResponseEntity<LessonAssignmentResponse> create(
			@AuthenticationPrincipal CurrentUser currentUser,
			@PathVariable UUID roomId,
			@Valid @RequestBody CreateLessonAssignmentRequest request
	) {
		LessonAssignmentResponse response = assignmentService.create(currentUser.userId(), roomId, request);
		URI location = URI.create(
				"/teacher/rooms/" + roomId + "/lesson-assignments/" + response.id()
		);
		return ResponseEntity.created(location).body(response);
	}

	@PatchMapping("/{assignmentId}")
	public LessonAssignmentResponse update(
			@AuthenticationPrincipal CurrentUser currentUser,
			@PathVariable UUID roomId,
			@PathVariable UUID assignmentId,
			@Valid @RequestBody UpdateLessonAssignmentRequest request
	) {
		return assignmentService.update(currentUser.userId(), roomId, assignmentId, request);
	}

	@DeleteMapping("/{assignmentId}")
	public ResponseEntity<Void> delete(
			@AuthenticationPrincipal CurrentUser currentUser,
			@PathVariable UUID roomId,
			@PathVariable UUID assignmentId,
			@RequestParam long version
	) {
		assignmentService.delete(currentUser.userId(), roomId, assignmentId, version);
		return ResponseEntity.noContent().build();
	}

	@PutMapping("/order")
	public List<LessonAssignmentResponse> reorder(
			@AuthenticationPrincipal CurrentUser currentUser,
			@PathVariable UUID roomId,
			@Valid @RequestBody LessonAssignmentOrderRequest request
	) {
		return assignmentService.reorder(currentUser.userId(), roomId, request);
	}
}
