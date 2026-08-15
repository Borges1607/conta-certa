package com.ifsc.contacerta.dto.institution;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateInstitutionRequest(
		@NotBlank @Size(max = 160) String name,
		@NotBlank String cnpj,
		@NotBlank @Email @Size(max = 254) String contactEmail,
		@NotBlank @Size(max = 24) String contactPhone
) {
}
