package com.ifsc.contacerta.mapper;

import com.ifsc.contacerta.dto.institution.InstitutionResponse;
import com.ifsc.contacerta.dto.institution.InstitutionOptionResponse;
import com.ifsc.contacerta.entity.Institution;

public final class InstitutionMapper {

	private InstitutionMapper() {
	}

	public static InstitutionResponse toResponse(Institution institution) {
		return new InstitutionResponse(
				institution.getId(),
				institution.getName(),
				institution.getCnpj(),
				institution.getContactEmail(),
				institution.getContactPhone(),
				institution.isActive(),
				institution.getCreatedAt(),
				institution.getUpdatedAt(),
				institution.getVersion()
		);
	}

	public static InstitutionOptionResponse toOptionResponse(Institution institution) {
		return new InstitutionOptionResponse(
				institution.getId(),
				institution.getName(),
				institution.getCnpj()
		);
	}
}
