package com.ifsc.contacerta.repository;

import com.ifsc.contacerta.entity.Institution;
import com.ifsc.contacerta.entity.User;
import com.ifsc.contacerta.model.AccountStatus;
import com.ifsc.contacerta.model.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class UserRepositoryTest {

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private InstitutionRepository institutionRepository;

	@Test
	void deveLocalizarEmailSemDiferenciarMaiusculas() {
		Institution institution = institutionRepository.save(new Institution(
				"Instituto Exemplo", "12345678000190", "contato@example.com", "48999990000", true
		));
		userRepository.save(new User(
				Role.TEACHER,
				AccountStatus.PENDING,
				"Maria Souza",
				"Maria.Souza@example.com",
				"PROF-100",
				institution
		));

		assertThat(userRepository.findByEmailIgnoreCase("maria.souza@EXAMPLE.COM"))
				.isPresent()
				.get()
				.extracting(User::getFullName)
				.isEqualTo("Maria Souza");
	}
}
