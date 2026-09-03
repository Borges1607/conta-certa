package com.ifsc.contacerta.dto.admin;

import com.ifsc.contacerta.dto.institution.InstitutionSummaryResponse;
import com.ifsc.contacerta.model.AccountStatus;

import java.time.Instant;
import java.util.UUID;

public record AdminTeacherResponse(
		UUID id,
		String fullName,
		String email,
		String registrationNumber,
		InstitutionSummaryResponse institution,
		AccountStatus status,
		boolean emailVerified,
		long version,
		Instant createdAt,
		Instant updatedAt,
		Instant lastLoginAt
) {
}
