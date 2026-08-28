package com.ifsc.contacerta.controller;

import com.ifsc.contacerta.dto.shared.PageResponse;
import com.ifsc.contacerta.dto.video.CreateVideoRequest;
import com.ifsc.contacerta.dto.video.PatchVideoRequest;
import com.ifsc.contacerta.dto.video.TeacherVideoResponse;
import com.ifsc.contacerta.exception.ApiException;
import com.ifsc.contacerta.security.CurrentUser;
import com.ifsc.contacerta.service.VideoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/teacher/videos")
@RequiredArgsConstructor
public class TeacherVideoController {

	private static final Set<String> SORT_FIELDS = Set.of("title", "createdAt", "updatedAt");

	private final VideoService videoService;

	@GetMapping
	public PageResponse<TeacherVideoResponse> list(
			@AuthenticationPrincipal CurrentUser currentUser,
			@RequestParam(required = false) String search,
			@RequestParam(required = false) String category,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size,
			@RequestParam(defaultValue = "createdAt,desc") String sort
	) {
		return videoService.list(currentUser.userId(), search, category, pageable(page, size, sort));
	}

	@PostMapping
	public ResponseEntity<TeacherVideoResponse> create(
			@AuthenticationPrincipal CurrentUser currentUser,
			@Valid @RequestBody CreateVideoRequest request
	) {
		TeacherVideoResponse response = videoService.create(currentUser.userId(), request);
		return ResponseEntity.created(URI.create("/teacher/videos/" + response.id())).body(response);
	}

	@GetMapping("/{videoId}")
	public TeacherVideoResponse get(
			@AuthenticationPrincipal CurrentUser currentUser,
			@PathVariable UUID videoId
	) {
		return videoService.get(currentUser.userId(), videoId);
	}

	@PatchMapping("/{videoId}")
	public TeacherVideoResponse update(
			@AuthenticationPrincipal CurrentUser currentUser,
			@PathVariable UUID videoId,
			@Valid @RequestBody PatchVideoRequest request
	) {
		return videoService.update(currentUser.userId(), videoId, request);
	}

	@DeleteMapping("/{videoId}")
	public ResponseEntity<Void> archive(
			@AuthenticationPrincipal CurrentUser currentUser,
			@PathVariable UUID videoId
	) {
		videoService.archive(currentUser.userId(), videoId);
		return ResponseEntity.noContent().build();
	}

	private Pageable pageable(int page, int size, String sort) {
		if (page < 0 || size < 1 || size > 100) {
			throw invalidPagination();
		}
		String[] parts = sort.split(",", -1);
		if (parts.length > 2 || parts[0].isBlank() || !SORT_FIELDS.contains(parts[0])) {
			throw invalidSort();
		}
		Sort.Direction direction = parts.length == 1
				? Sort.Direction.ASC
				: Sort.Direction.fromOptionalString(parts[1]).orElseThrow(this::invalidSort);
		return PageRequest.of(page, size, Sort.by(direction, parts[0]));
	}

	private ApiException invalidPagination() {
		return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "VALIDATION_ERROR", "Pagination values are invalid.");
	}

	private ApiException invalidSort() {
		return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "VALIDATION_ERROR", "Video sort is invalid.");
	}
}
