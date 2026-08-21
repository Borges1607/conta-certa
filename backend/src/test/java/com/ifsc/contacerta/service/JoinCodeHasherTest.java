package com.ifsc.contacerta.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JoinCodeHasherTest {

	@Test
	void deveNormalizarOCodigoAntesDeGerarHashSha256() {
		JoinCodeHasher hasher = new JoinCodeHasher();

		String hash = hasher.hash(" abc234 ");

		assertThat(hash).isEqualTo("8c640c4e71f90160b2b3615af86739e6b15ddc877ae79e18aada753565f756c4");
	}

	@Test
	void deveProduzirHashSha256EmHexadecimalMinusculo() {
		JoinCodeHasher hasher = new JoinCodeHasher();

		String hash = hasher.hash("DEF567");

		assertThat(hash).isEqualTo("dc7904f769c857873b9fc48880f556ecb93579ae3ead145d52d4326b83bbd285");
	}
}
