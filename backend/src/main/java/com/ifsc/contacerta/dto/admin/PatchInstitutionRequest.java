package com.ifsc.contacerta.dto.admin;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PatchInstitutionRequest(
		@Size(max = 160) String name,
		String cnpj,
		@Email @Size(max = 254) String contactEmail,
		@Pattern(regexp = "^\\+[1-9]\\d{7,14}$", message = "must be a valid E.164 phone number") String contactPhone,
		@Min(0) Long version
) {
}
