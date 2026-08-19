package com.ifsc.contacerta.service;

import com.ifsc.contacerta.dto.auth.UserResponse;
import com.ifsc.contacerta.dto.institution.InstitutionSummaryResponse;
import com.ifsc.contacerta.entity.User;
import org.springframework.stereotype.Component;

@Component
public class UserResponseMapper {

	public UserResponse toResponse(User user) {
		var institution = user.getInstitution();
		return new UserResponse(
				user.getId(),
				user.getRole(),
				user.getStatus(),
				user.getFullName(),
				user.getEmail(),
				user.getRegistrationNumber(),
				institution == null ? null : new InstitutionSummaryResponse(
						institution.getId(),
						institution.getName(),
						institution.getCnpj(),
						institution.getContactEmail(),
						institution.getContactPhone(),
						institution.isActive()
				),
				user.getEmailVerifiedAt() != null,
				user.isMustChangePassword()
		);
	}
}
