package com.ifsc.contacerta.controller;

import com.ifsc.contacerta.dto.material.CreateMaterialRequest;
import com.ifsc.contacerta.dto.material.PatchMaterialRequest;
import com.ifsc.contacerta.dto.material.TeacherMaterialResponse;
import com.ifsc.contacerta.dto.shared.PageResponse;
import com.ifsc.contacerta.exception.ApiException;
import com.ifsc.contacerta.model.MaterialKind;
import com.ifsc.contacerta.security.CurrentUser;
import com.ifsc.contacerta.service.MaterialService;
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
@RequestMapping("/teacher/materials")
@RequiredArgsConstructor
public class TeacherMaterialController {

	private static final Set<String> SORT_FIELDS = Set.of("title", "createdAt", "updatedAt");

	private final MaterialService materialService;

	@GetMapping
	public PageResponse<TeacherMaterialResponse> list(
			@AuthenticationPrincipal CurrentUser currentUser,
			@RequestParam(required = false) String search,
			@RequestParam(required = false) MaterialKind kind,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size,
			@RequestParam(defaultValue = "createdAt,desc") String sort
	) {
		return materialService.list(currentUser.userId(), search, kind, pageable(page, size, sort));
	}

	@PostMapping
	public ResponseEntity<TeacherMaterialResponse> create(
			@AuthenticationPrincipal CurrentUser currentUser,
			@Valid @RequestBody CreateMaterialRequest request
	) {
		TeacherMaterialResponse response = materialService.create(currentUser.userId(), request);
		return ResponseEntity.created(URI.create("/teacher/materials/" + response.id())).body(response);
	}

	@GetMapping("/{materialId}")
	public TeacherMaterialResponse get(
			@AuthenticationPrincipal CurrentUser currentUser, @PathVariable UUID materialId
	) {
		return materialService.get(currentUser.userId(), materialId);
	}

	@PatchMapping("/{materialId}")
	public TeacherMaterialResponse update(
			@AuthenticationPrincipal CurrentUser currentUser,
			@PathVariable UUID materialId,
			@Valid @RequestBody PatchMaterialRequest request
	) {
		return materialService.update(currentUser.userId(), materialId, request);
	}

	@DeleteMapping("/{materialId}")
	public ResponseEntity<Void> archive(
			@AuthenticationPrincipal CurrentUser currentUser, @PathVariable UUID materialId
	) {
		materialService.archive(currentUser.userId(), materialId);
		return ResponseEntity.noContent().build();
	}

	private Pageable pageable(int page, int size, String sort) {
		if (page < 0 || size < 1 || size > 100) {
			throw validation("Pagination values are invalid.");
		}
		String[] parts = sort.split(",", -1);
		if (parts.length > 2 || parts[0].isBlank() || !SORT_FIELDS.contains(parts[0])) {
			throw validation("Material sort is invalid.");
		}
		Sort.Direction direction = parts.length == 1
				? Sort.Direction.ASC
				: Sort.Direction.fromOptionalString(parts[1])
						.orElseThrow(() -> validation("Material sort is invalid."));
		return PageRequest.of(page, size, Sort.by(direction, parts[0]));
	}

	private ApiException validation(String detail) {
		return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "VALIDATION_ERROR", detail);
	}
}
