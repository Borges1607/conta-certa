package com.ifsc.contacerta.mapper;

import com.ifsc.contacerta.dto.admin.AdminTeacherResponse;
import com.ifsc.contacerta.entity.User;

import java.time.Instant;

public final class AdminTeacherMapper {

	private AdminTeacherMapper() {
	}

	public static AdminTeacherResponse toResponse(User teacher, Instant lastLoginAt) {
		return new AdminTeacherResponse(
				teacher.getId(),
				teacher.getFullName(),
				teacher.getEmail(),
				teacher.getRegistrationNumber(),
				teacher.getInstitution() == null ? null : InstitutionMapper.toSummaryResponse(teacher.getInstitution()),
				teacher.getStatus(),
				teacher.getEmailVerifiedAt() != null,
				teacher.getVersion(),
				teacher.getCreatedAt(),
				teacher.getUpdatedAt(),
				lastLoginAt
		);
	}
}
