package com.ifsc.contacerta.controller;

import com.ifsc.contacerta.dto.admin.AdminTeacherResponse;
import com.ifsc.contacerta.dto.admin.CreateTeacherRequest;
import com.ifsc.contacerta.dto.admin.PatchTeacherRequest;
import com.ifsc.contacerta.dto.shared.PageResponse;
import com.ifsc.contacerta.model.AccountStatus;
import com.ifsc.contacerta.service.AdminTeacherService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
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
@RequestMapping("/admin/teachers")
@RequiredArgsConstructor
public class AdminTeacherController {
	private static final Set<String> SORT_FIELDS = Set.of("fullName", "createdAt", "updatedAt", "status");
	private final AdminTeacherService service;
	private final AdminPageableFactory pageableFactory = new AdminPageableFactory();

	@GetMapping
	public PageResponse<AdminTeacherResponse> list(
			@RequestParam(required = false) String search,
			@RequestParam(required = false) AccountStatus status,
			@RequestParam(required = false) UUID institutionId,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size,
			@RequestParam(defaultValue = "createdAt,desc") String sort
	) {
		Page<AdminTeacherResponse> result = service.list(search, status, institutionId,
				pageableFactory.create(page, size, sort, SORT_FIELDS, "INVALID_TEACHER_SORT"));
		return PageResponse.from(result);
	}

	@PostMapping
	public ResponseEntity<AdminTeacherResponse> create(@Valid @RequestBody CreateTeacherRequest request) {
		var teacher = service.create(request);
		AdminTeacherResponse response = service.get(teacher.getId());
		return ResponseEntity.created(URI.create("/admin/teachers/" + response.id())).body(response);
	}

	@GetMapping("/{teacherId}")
	public AdminTeacherResponse get(@PathVariable UUID teacherId) {
		return service.get(teacherId);
	}

	@PatchMapping("/{teacherId}")
	public AdminTeacherResponse update(@PathVariable UUID teacherId, @Valid @RequestBody PatchTeacherRequest request) {
		return service.update(teacherId, request);
	}

	@PostMapping("/{teacherId}/activate")
	public AdminTeacherResponse activate(@PathVariable UUID teacherId) {
		return service.activate(teacherId);
	}

	@PostMapping("/{teacherId}/deactivate")
	public AdminTeacherResponse deactivate(@PathVariable UUID teacherId) {
		return service.deactivate(teacherId);
	}

	@PostMapping("/{teacherId}/password-reset")
	public ResponseEntity<Void> passwordReset(@PathVariable UUID teacherId) {
		service.passwordReset(teacherId);
		return ResponseEntity.accepted().build();
	}
}
