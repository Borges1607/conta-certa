package com.ifsc.contacerta.controller;

import com.ifsc.contacerta.dto.admin.AdminInstitutionResponse;
import com.ifsc.contacerta.dto.admin.PatchInstitutionRequest;
import com.ifsc.contacerta.dto.institution.CreateInstitutionRequest;
import com.ifsc.contacerta.dto.shared.PageResponse;
import com.ifsc.contacerta.service.AdminInstitutionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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
@RequestMapping("/admin/institutions")
@RequiredArgsConstructor
public class AdminInstitutionController {

	private static final Set<String> SORT_FIELDS = Set.of("name", "createdAt", "updatedAt");

	private final AdminInstitutionService service;
	private final AdminPageableFactory pageableFactory = new AdminPageableFactory();

	@GetMapping
	public PageResponse<AdminInstitutionResponse> list(
			@RequestParam(required = false) String search,
			@RequestParam(required = false) Boolean active,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size,
			@RequestParam(defaultValue = "createdAt,desc") String sort
	) {
		return PageResponse.from(service.list(search, active, pageableFactory.create(page, size, sort, SORT_FIELDS, "INVALID_INSTITUTION_SORT")));
	}

	@PostMapping
	public ResponseEntity<AdminInstitutionResponse> create(@Valid @RequestBody CreateInstitutionRequest request) {
		AdminInstitutionResponse response = service.create(request);
		return ResponseEntity.created(URI.create("/admin/institutions/" + response.id())).body(response);
	}

	@GetMapping("/{institutionId}")
	public AdminInstitutionResponse get(@PathVariable UUID institutionId) {
		return service.get(institutionId);
	}

	@PatchMapping("/{institutionId}")
	public AdminInstitutionResponse update(
			@PathVariable UUID institutionId,
			@Valid @RequestBody PatchInstitutionRequest request
	) {
		return service.update(institutionId, request);
	}

	@PostMapping("/{institutionId}/activate")
	public AdminInstitutionResponse activate(@PathVariable UUID institutionId) {
		return service.activate(institutionId);
	}

	@PostMapping("/{institutionId}/deactivate")
	public AdminInstitutionResponse deactivate(@PathVariable UUID institutionId) {
		return service.deactivate(institutionId);
	}

	@DeleteMapping("/{institutionId}")
	public ResponseEntity<Void> delete(@PathVariable UUID institutionId) {
		service.delete(institutionId);
		return ResponseEntity.noContent().build();
	}
}
