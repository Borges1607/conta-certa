package com.ifsc.contacerta.service;

import com.ifsc.contacerta.dto.admin.AdminFinancialTipResponse;
import com.ifsc.contacerta.dto.admin.CreateFinancialTipRequest;
import com.ifsc.contacerta.dto.admin.PatchFinancialTipRequest;
import com.ifsc.contacerta.entity.FinancialTip;
import com.ifsc.contacerta.exception.ApiException;
import com.ifsc.contacerta.mapper.AdminFinancialTipMapper;
import com.ifsc.contacerta.repository.FinancialTipRepository;
import com.ifsc.contacerta.specification.FinancialTipSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminFinancialTipService {

	private final FinancialTipRepository repository;
	private final Clock clock;

	@Transactional(readOnly = true)
	public Page<AdminFinancialTipResponse> list(String search, Boolean active, LocalDate publicationDate, Pageable pageable) {
		return repository.findAll(FinancialTipSpecification.filtered(search, active, publicationDate), pageable)
				.map(AdminFinancialTipMapper::toResponse);
	}

	@Transactional(readOnly = true)
	public AdminFinancialTipResponse get(UUID tipId) {
		return AdminFinancialTipMapper.toResponse(requireTip(tipId));
	}

	@Transactional
	public AdminFinancialTipResponse create(CreateFinancialTipRequest request) {
		String sourceUrl = normalizeSourceUrl(request.sourceUrl());
		FinancialTip tip = new FinancialTip(
				request.title().trim(), request.content().trim(), sourceUrl, request.publicationDate(),
				Boolean.TRUE.equals(request.active())
		);
		return AdminFinancialTipMapper.toResponse(repository.save(tip));
	}

	@Transactional
	public AdminFinancialTipResponse update(UUID tipId, PatchFinancialTipRequest request) {
		FinancialTip tip = requireTip(tipId);
		requireVersion(tip, request.version());
		tip.update(request.title().trim(), request.content().trim(), normalizeSourceUrl(request.sourceUrl()), request.publicationDate());
		return AdminFinancialTipMapper.toResponse(tip);
	}

	@Transactional
	public AdminFinancialTipResponse activate(UUID tipId) {
		FinancialTip tip = requireTip(tipId);
		tip.activate();
		return AdminFinancialTipMapper.toResponse(tip);
	}

	@Transactional
	public AdminFinancialTipResponse deactivate(UUID tipId) {
		FinancialTip tip = requireTip(tipId);
		tip.deactivate();
		return AdminFinancialTipMapper.toResponse(tip);
	}

	@Transactional
	public void archive(UUID tipId) {
		requireTip(tipId).archive(clock.instant());
	}

	private FinancialTip requireTip(UUID tipId) {
		return repository.findByIdAndArchivedAtIsNull(tipId).orElseThrow(() ->
				new ApiException(HttpStatus.NOT_FOUND, "FINANCIAL_TIP_NOT_FOUND", "Financial tip was not found."));
	}

	private void requireVersion(FinancialTip tip, Long expectedVersion) {
		if (expectedVersion == null || tip.getVersion() != expectedVersion) {
			throw new ApiException(HttpStatus.CONFLICT, "VERSION_CONFLICT", "The financial tip was changed by another request.");
		}
	}

	private String normalizeSourceUrl(String sourceUrl) {
		if (sourceUrl == null || sourceUrl.isBlank()) {
			return null;
		}
		String normalized = sourceUrl.trim();
		try {
			URI uri = new URI(normalized);
			String scheme = uri.getScheme();
			if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https")) || uri.getHost() == null) {
				throw invalidSourceUrl();
			}
		} catch (URISyntaxException exception) {
			throw invalidSourceUrl();
		}
		return normalized;
	}

	private ApiException invalidSourceUrl() {
		return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "INVALID_SOURCE_URL", "Source URL must be a valid HTTP or HTTPS URL.");
	}
}
