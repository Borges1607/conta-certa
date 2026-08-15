package com.ifsc.contacerta.repository;

import com.ifsc.contacerta.entity.Institution;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class InstitutionRepositoryTest {

	@Autowired
	private InstitutionRepository institutionRepository;

	@Test
	void deveListarSomenteInstituicoesAtivasOrdenadasPorNome() {
		institutionRepository.save(new Institution(
				"Zeta Escola", "12345678000190", "zeta@example.com", "48999990000", true
		));
		institutionRepository.save(new Institution(
				"Alfa Escola", "98765432000198", "alfa@example.com", "48999990001", true
		));
		institutionRepository.save(new Institution(
				"Beta Inativa", "11222333000181", "beta@example.com", "48999990002", false
		));

		List<Institution> result = institutionRepository.findByActiveTrueOrderByNameAsc();

		assertThat(result)
				.extracting(Institution::getName)
				.containsExactly("Alfa Escola", "Zeta Escola");
	}
}
