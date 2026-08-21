package com.ifsc.contacerta.controller;

import com.ifsc.contacerta.dto.lesson.CreateLessonRequest;
import com.ifsc.contacerta.dto.lesson.LessonDetailResponse;
import com.ifsc.contacerta.dto.lesson.LessonSummaryResponse;
import com.ifsc.contacerta.dto.lesson.UpdateLessonRequest;
import com.ifsc.contacerta.dto.shared.PageResponse;
import com.ifsc.contacerta.exception.ApiException;
import com.ifsc.contacerta.security.CurrentUser;
import com.ifsc.contacerta.service.LessonService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/teacher/lessons")
@RequiredArgsConstructor
public class TeacherLessonController {

	private final LessonService lessonService;

	@GetMapping
	public PageResponse<LessonSummaryResponse> list(
			@AuthenticationPrincipal CurrentUser currentUser,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size
	) {
		if (page < 0 || size < 1 || size > 100) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "INVALID_PAGE", "Pagination values are invalid.");
		}
		return lessonService.list(currentUser.userId(), PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
	}

	@PostMapping
	public ResponseEntity<LessonDetailResponse> create(@AuthenticationPrincipal CurrentUser currentUser, @Valid @RequestBody CreateLessonRequest request) {
		LessonDetailResponse response = lessonService.create(currentUser.userId(), request);
		return ResponseEntity.created(URI.create("/teacher/lessons/" + response.id())).body(response);
	}

	@GetMapping("/{lessonId}")
	public LessonDetailResponse get(@AuthenticationPrincipal CurrentUser currentUser, @PathVariable UUID lessonId) {
		return lessonService.get(currentUser.userId(), lessonId);
	}

	@PatchMapping("/{lessonId}")
	public LessonDetailResponse update(@AuthenticationPrincipal CurrentUser currentUser, @PathVariable UUID lessonId, @Valid @RequestBody UpdateLessonRequest request) {
		return lessonService.update(currentUser.userId(), lessonId, request);
	}

	@PostMapping("/{lessonId}/publish")
	public LessonDetailResponse publish(@AuthenticationPrincipal CurrentUser currentUser, @PathVariable UUID lessonId) {
		return lessonService.publish(currentUser.userId(), lessonId);
	}

	@PostMapping("/{lessonId}/archive")
	public LessonDetailResponse archive(@AuthenticationPrincipal CurrentUser currentUser, @PathVariable UUID lessonId) {
		return lessonService.archive(currentUser.userId(), lessonId);
	}

	@PostMapping("/{lessonId}/duplicate")
	public LessonDetailResponse duplicate(@AuthenticationPrincipal CurrentUser currentUser, @PathVariable UUID lessonId) {
		return lessonService.duplicate(currentUser.userId(), lessonId);
	}
}
