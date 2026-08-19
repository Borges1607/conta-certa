package com.ifsc.contacerta.service;

import com.ifsc.contacerta.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordPolicyTest {

	private final PasswordPolicy policy = new PasswordPolicy();

	@Test
	void deveAceitarSenhaDentroDaPolitica() {
		assertThatCode(() -> policy.validate("Senha123")).doesNotThrowAnyException();
	}

	@ParameterizedTest
	@NullSource
	@ValueSource(strings = {"curta1", "somenteletras", "12345678"})
	void deveRejeitarSenhaForaDaPolitica(String password) {
		assertThatThrownBy(() -> policy.validate(password))
				.isInstanceOfSatisfying(ApiException.class, exception -> {
					assertThat(exception.getCode()).isEqualTo("INVALID_PASSWORD");
					assertThat(exception.getStatus().value()).isEqualTo(422);
				});
	}

	@Test
	void deveRejeitarSenhaMaiorQueSetentaEDoisCaracteres() {
		String password = "A1" + "x".repeat(71);

		assertThatThrownBy(() -> policy.validate(password))
				.isInstanceOfSatisfying(ApiException.class,
						exception -> assertThat(exception.getCode()).isEqualTo("INVALID_PASSWORD"));
	}
}
