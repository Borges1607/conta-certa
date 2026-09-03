package com.ifsc.contacerta.controller;

import com.ifsc.contacerta.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminPageableFactoryTest {

	private final AdminPageableFactory factory = new AdminPageableFactory();

	@Test
	void criaPaginaComOrdenacaoDescendentePermitida() {
		Pageable pageable = factory.create(2, 50, "name,desc", Set.of("name"), "INVALID_SORT");

		assertThat(pageable.getPageNumber()).isEqualTo(2);
		assertThat(pageable.getPageSize()).isEqualTo(50);
		assertThat(pageable.getSort().getOrderFor("name").getDirection()).isEqualTo(Sort.Direction.DESC);
	}

	@Test
	void rejeitaPaginaForaDosLimites() {
		assertThatThrownBy(() -> factory.create(-1, 20, "name,asc", Set.of("name"), "INVALID_SORT"))
				.isInstanceOfSatisfying(ApiException.class,
					error -> assertThat(error.getCode()).isEqualTo("INVALID_PAGE"));

		assertThatThrownBy(() -> factory.create(0, 101, "name,asc", Set.of("name"), "INVALID_SORT"))
				.isInstanceOfSatisfying(ApiException.class,
					error -> assertThat(error.getCode()).isEqualTo("INVALID_PAGE"));
	}

	@Test
	void rejeitaCampoEDirecaoDesconhecidos() {
		assertThatThrownBy(() -> factory.create(0, 20, "email,asc", Set.of("name"), "INVALID_SORT"))
				.isInstanceOfSatisfying(ApiException.class,
					error -> assertThat(error.getCode()).isEqualTo("INVALID_SORT"));

		assertThatThrownBy(() -> factory.create(0, 20, "name,sideways", Set.of("name"), "INVALID_SORT"))
				.isInstanceOfSatisfying(ApiException.class,
					error -> assertThat(error.getCode()).isEqualTo("INVALID_SORT"));
	}
}
