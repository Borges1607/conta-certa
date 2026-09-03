package com.ifsc.contacerta.service;

import com.ifsc.contacerta.dto.admin.AdminInstitutionResponse;
import com.ifsc.contacerta.dto.admin.PatchInstitutionRequest;
import com.ifsc.contacerta.dto.institution.CreateInstitutionRequest;
import com.ifsc.contacerta.entity.Institution;
import com.ifsc.contacerta.exception.ApiException;
import com.ifsc.contacerta.mapper.AdminInstitutionMapper;
import com.ifsc.contacerta.model.Role;
import com.ifsc.contacerta.repository.AdminHistoryQueryRepository;
import com.ifsc.contacerta.repository.InstitutionRepository;
import com.ifsc.contacerta.repository.UserRepository;
import com.ifsc.contacerta.specification.InstitutionSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminInstitutionService {

	private final InstitutionRepository institutionRepository;
	private final UserRepository userRepository;
	private final AdminHistoryQueryRepository historyQueryRepository;

	@Transactional(readOnly = true)
	public Page<com.ifsc.contacerta.dto.admin.AdminInstitutionResponse> list(String search, Boolean active, Pageable pageable) {
		return institutionRepository.findAll(InstitutionSpecification.filtered(search, active), pageable)
				.map(this::toResponse);
	}

	@Transactional(readOnly = true)
	public AdminInstitutionResponse get(UUID institutionId) {
		return toResponse(requireInstitution(institutionId));
	}

	@Transactional
	public AdminInstitutionResponse create(CreateInstitutionRequest request) {
		String cnpj = digitsOnly(request.cnpj());
		validateCnpj(cnpj);
		String phone = request.contactPhone().trim();
		validatePhone(phone);
		if (institutionRepository.findByCnpj(cnpj).isPresent()) {
			throw new ApiException(HttpStatus.CONFLICT, "CNPJ_ALREADY_EXISTS", "CNPJ is already registered.");
		}
		Institution institution = new Institution(
				request.name().trim(), cnpj, request.contactEmail().trim().toLowerCase(Locale.ROOT), phone, true
		);
		return toResponse(institutionRepository.save(institution));
	}

	@Transactional
	public AdminInstitutionResponse update(UUID institutionId, PatchInstitutionRequest request) {
		Institution institution = requireInstitution(institutionId);
		requireVersion(institution, request.version());
		String name = request.name() == null ? institution.getName() : request.name().trim();
		String cnpj = request.cnpj() == null ? institution.getCnpj() : digitsOnly(request.cnpj());
		String email = request.contactEmail() == null ? institution.getContactEmail() : request.contactEmail().trim().toLowerCase(Locale.ROOT);
		String phone = request.contactPhone() == null ? institution.getContactPhone() : request.contactPhone().trim();
		validateName(name);
		validateCnpj(cnpj);
		validatePhone(phone);
		if (!cnpj.equals(institution.getCnpj()) && institutionRepository.findByCnpj(cnpj).filter(other -> !other.getId().equals(institutionId)).isPresent()) {
			throw new ApiException(HttpStatus.CONFLICT, "CNPJ_ALREADY_EXISTS", "CNPJ is already registered.");
		}
		institution.update(name, cnpj, email, phone);
		return toResponse(institution);
	}

	@Transactional
	public AdminInstitutionResponse activate(UUID institutionId) {
		Institution institution = requireInstitution(institutionId);
		institution.activate();
		return toResponse(institution);
	}

	@Transactional
	public AdminInstitutionResponse deactivate(UUID institutionId) {
		Institution institution = requireInstitution(institutionId);
		institution.deactivate();
		return toResponse(institution);
	}

	@Transactional
	public void delete(UUID institutionId) {
		Institution institution = requireInstitution(institutionId);
		if (historyQueryRepository.hasInstitutionHistory(institutionId)) {
			throw new ApiException(HttpStatus.CONFLICT, "INSTITUTION_HAS_HISTORY", "Institutions with history cannot be deleted.");
		}
		institutionRepository.delete(institution);
	}

	private Institution requireInstitution(UUID institutionId) {
		return institutionRepository.findById(institutionId).orElseThrow(() ->
				new ApiException(HttpStatus.NOT_FOUND, "INSTITUTION_NOT_FOUND", "Institution was not found."));
	}

	private AdminInstitutionResponse toResponse(Institution institution) {
		return AdminInstitutionMapper.toResponse(
				institution,
				userRepository.countByInstitutionIdAndRole(institution.getId(), Role.TEACHER),
				userRepository.countByInstitutionIdAndRole(institution.getId(), Role.STUDENT)
		);
	}

	private void requireVersion(Institution institution, Long version) {
		if (version == null || version != institution.getVersion()) {
			throw new ApiException(HttpStatus.CONFLICT, "VERSION_CONFLICT", "The institution was changed by another request.");
		}
	}

	private String digitsOnly(String value) {
		return value == null ? "" : value.replaceAll("\\D", "");
	}

	private void validateName(String name) {
		if (name == null || name.isBlank() || name.length() > 160) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "INVALID_INSTITUTION_NAME", "Institution name is invalid.");
		}
	}

	private void validateCnpj(String cnpj) {
		if (cnpj.length() != 14) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "INVALID_CNPJ", "CNPJ must contain 14 digits.");
		}
	}

	private void validatePhone(String phone) {
		if (phone == null || !phone.matches("^\\+[1-9]\\d{7,14}$")) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "INVALID_PHONE", "Phone must use E.164 format.");
		}
	}
}
