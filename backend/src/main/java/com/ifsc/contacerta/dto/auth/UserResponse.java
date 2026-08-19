package com.ifsc.contacerta.dto.auth;

import com.ifsc.contacerta.dto.institution.InstitutionSummaryResponse;
import com.ifsc.contacerta.model.AccountStatus;
import com.ifsc.contacerta.model.Role;

import java.util.UUID;

public record UserResponse(
		UUID id,
		Role role,
		AccountStatus status,
		String fullName,
		String email,
		String registrationNumber,
		InstitutionSummaryResponse institution,
		boolean emailVerified,
		boolean mustChangePassword
) {
}
