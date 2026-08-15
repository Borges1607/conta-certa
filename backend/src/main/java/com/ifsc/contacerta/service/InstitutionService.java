package com.ifsc.contacerta.service;

import com.ifsc.contacerta.dto.institution.CreateInstitutionRequest;
import com.ifsc.contacerta.dto.institution.InstitutionResponse;
import com.ifsc.contacerta.dto.institution.InstitutionOptionResponse;
import com.ifsc.contacerta.entity.Institution;
import com.ifsc.contacerta.exception.ApiException;
import com.ifsc.contacerta.mapper.InstitutionMapper;
import com.ifsc.contacerta.repository.InstitutionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class InstitutionService {

	private final InstitutionRepository institutionRepository;

	@Transactional
	public InstitutionResponse create(CreateInstitutionRequest request) {
		String cnpj = digitsOnly(request.cnpj());
		if (institutionRepository.findByCnpj(cnpj).isPresent()) {
			throw new ApiException(
					HttpStatus.CONFLICT,
					"CNPJ_ALREADY_EXISTS",
					"CNPJ is already registered."
			);
		}

		Institution institution = new Institution(
				request.name().trim(),
				cnpj,
				request.contactEmail().trim().toLowerCase(Locale.ROOT),
				digitsOnly(request.contactPhone()),
				true
		);

		return InstitutionMapper.toResponse(institutionRepository.save(institution));
	}

	@Transactional(readOnly = true)
	public List<InstitutionOptionResponse> listActiveOptions() {
		return institutionRepository.findByActiveTrueOrderByNameAsc().stream()
				.map(InstitutionMapper::toOptionResponse)
				.toList();
	}

	private String digitsOnly(String value) {
		return value.replaceAll("\\D", "");
	}
}
