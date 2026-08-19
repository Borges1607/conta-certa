package com.ifsc.contacerta.dto.auth;

import com.ifsc.contacerta.model.AccountStatus;
import com.ifsc.contacerta.model.Role;

import java.util.UUID;

public record UserResponse(
		UUID id,
		String fullName,
		String email,
		Role role,
		AccountStatus status,
		UUID institutionId,
		boolean mustChangePassword
) {
}
