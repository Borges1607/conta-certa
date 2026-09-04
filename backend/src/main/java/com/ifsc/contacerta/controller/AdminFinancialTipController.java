package com.ifsc.contacerta.controller;

import com.ifsc.contacerta.dto.admin.AdminFinancialTipResponse;
import com.ifsc.contacerta.dto.admin.CreateFinancialTipRequest;
import com.ifsc.contacerta.dto.admin.PatchFinancialTipRequest;
import com.ifsc.contacerta.dto.shared.PageResponse;
import com.ifsc.contacerta.service.AdminFinancialTipService;
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
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/admin/financial-tips")
@RequiredArgsConstructor
public class AdminFinancialTipController {

	private static final Set<String> SORT_FIELDS = Set.of("title", "publicationDate", "createdAt", "updatedAt");

	private final AdminFinancialTipService service;
	private final AdminPageableFactory pageableFactory = new AdminPageableFactory();

	@GetMapping
	public PageResponse<AdminFinancialTipResponse> list(
			@RequestParam(required = false) String search,
			@RequestParam(required = false) Boolean active,
			@RequestParam(required = false) LocalDate publicationDate,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size,
			@RequestParam(defaultValue = "createdAt,desc") String sort
	) {
		return PageResponse.from(service.list(search, active, publicationDate,
				pageableFactory.create(page, size, sort, SORT_FIELDS, "INVALID_FINANCIAL_TIP_SORT")));
	}

	@PostMapping
	public ResponseEntity<AdminFinancialTipResponse> create(@Valid @RequestBody CreateFinancialTipRequest request) {
		AdminFinancialTipResponse response = service.create(request);
		return ResponseEntity.created(URI.create("/admin/financial-tips/" + response.id())).body(response);
	}

	@GetMapping("/{tipId}")
	public AdminFinancialTipResponse get(@PathVariable UUID tipId) {
		return service.get(tipId);
	}

	@PatchMapping("/{tipId}")
	public AdminFinancialTipResponse update(
			@PathVariable UUID tipId,
			@Valid @RequestBody PatchFinancialTipRequest request
	) {
		return service.update(tipId, request);
	}

	@PostMapping("/{tipId}/activate")
	public AdminFinancialTipResponse activate(@PathVariable UUID tipId) {
		return service.activate(tipId);
	}

	@PostMapping("/{tipId}/deactivate")
	public AdminFinancialTipResponse deactivate(@PathVariable UUID tipId) {
		return service.deactivate(tipId);
	}

	@DeleteMapping("/{tipId}")
	public ResponseEntity<Void> archive(@PathVariable UUID tipId) {
		service.archive(tipId);
		return ResponseEntity.noContent().build();
	}
}
