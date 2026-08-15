package com.ifsc.contacerta.service;

import com.ifsc.contacerta.dto.institution.CreateInstitutionRequest;
import com.ifsc.contacerta.dto.institution.InstitutionResponse;
import com.ifsc.contacerta.dto.institution.InstitutionOptionResponse;
import com.ifsc.contacerta.entity.Institution;
import com.ifsc.contacerta.exception.ApiException;
import com.ifsc.contacerta.mapper.InstitutionMapper;
import com.ifsc.contacerta.repository.InstitutionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class InstitutionService {

	private final InstitutionRepository institutionRepository;

	public InstitutionService(InstitutionRepository institutionRepository) {
		this.institutionRepository = institutionRepository;
	}

	@Transactional
	public InstitutionResponse create(CreateInstitutionRequest request) {
		String cnpj = digitsOnly(request.cnpj());
		if (!isValidCnpj(cnpj)) {
			throw new ApiException(
					HttpStatus.UNPROCESSABLE_CONTENT,
					"INVALID_CNPJ",
					"CNPJ is invalid."
			);
		}
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
				request.contactEmail().trim().toLowerCase(java.util.Locale.ROOT),
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

	private boolean isValidCnpj(String cnpj) {
		if (cnpj.length() != 14 || cnpj.chars().distinct().count() == 1) {
			return false;
		}

		int firstDigit = calculateDigit(cnpj.substring(0, 12), new int[]{5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2});
		int secondDigit = calculateDigit(cnpj.substring(0, 12) + firstDigit, new int[]{6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2});

		return cnpj.charAt(12) - '0' == firstDigit && cnpj.charAt(13) - '0' == secondDigit;
	}

	private int calculateDigit(String base, int[] weights) {
		int sum = 0;
		for (int index = 0; index < weights.length; index++) {
			sum += (base.charAt(index) - '0') * weights[index];
		}
		int remainder = sum % 11;
		return remainder < 2 ? 0 : 11 - remainder;
	}
}
