package com.ifsc.contacerta.mapper;

import com.ifsc.contacerta.dto.admin.AdminInstitutionResponse;
import com.ifsc.contacerta.entity.Institution;

public final class AdminInstitutionMapper {

	private AdminInstitutionMapper() {
	}

	public static AdminInstitutionResponse toResponse(Institution institution, long teacherCount, long studentCount) {
		return new AdminInstitutionResponse(
				institution.getId(), institution.getName(), institution.getCnpj(), institution.getContactEmail(),
				institution.getContactPhone(), institution.isActive(), institution.getCreatedAt(), institution.getUpdatedAt(),
				institution.getVersion(), teacherCount, studentCount
		);
	}
}
