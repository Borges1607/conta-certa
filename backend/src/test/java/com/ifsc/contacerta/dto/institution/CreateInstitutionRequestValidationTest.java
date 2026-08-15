package com.ifsc.contacerta.dto.institution;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CreateInstitutionRequestValidationTest {

	private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

	@Test
	void deveAceitarCnpjValidoComOuSemMascara() {
		var formatted = requestWithCnpj("11.222.333/0001-81");
		var digitsOnly = requestWithCnpj("11222333000181");

		assertThat(validator.validate(formatted)).isEmpty();
		assertThat(validator.validate(digitsOnly)).isEmpty();
	}

	@Test
	void deveRejeitarCnpjInvalido() {
		var violations = validator.validate(requestWithCnpj("11.111.111/1111-11"));

		assertThat(violations)
				.singleElement()
				.satisfies(violation -> assertThat(violation.getPropertyPath().toString()).isEqualTo("cnpj"));
	}

	private CreateInstitutionRequest requestWithCnpj(String cnpj) {
		return new CreateInstitutionRequest(
				"Instituto Exemplo",
				cnpj,
				"contato@example.com",
				"48999990000"
		);
	}
}
