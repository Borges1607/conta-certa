package com.ifsc.contacerta.storage;

import com.ifsc.contacerta.entity.Institution;
import com.ifsc.contacerta.entity.StoredFile;
import com.ifsc.contacerta.entity.User;
import com.ifsc.contacerta.model.AccountStatus;
import com.ifsc.contacerta.model.Role;
import com.ifsc.contacerta.repository.InstitutionRepository;
import com.ifsc.contacerta.repository.UserRepository;
import com.ifsc.contacerta.service.MaterialFileValidator;
import com.ifsc.contacerta.support.PostgresIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class PostgresFileStorageTest extends PostgresIntegrationTest {

	@Autowired
	private FileStorage storage;
	@Autowired
	private InstitutionRepository institutionRepository;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private EntityManager entityManager;

	@Test
	void deveRecuperarBytesIdenticosAposLimparContexto() {
		Institution institution = institutionRepository.save(new Institution(
				"Instituto", "11222333000181", "contato@example.com", "48999990000", true
		));
		User teacher = userRepository.save(new User(
				Role.TEACHER, AccountStatus.ACTIVE, "Professora", "prof@example.com", "P-1", institution
		));
		byte[] content = {0x00, 0x01, (byte) 0xFF, 0x7F};

		StoredFile stored = storage.store(
				teacher,
				new MaterialFileValidator.ValidatedMaterialFile("aula.pdf", "application/pdf", content),
				Instant.parse("2026-08-28T12:00:00Z")
		);
		entityManager.flush();
		entityManager.clear();

		assertThat(storage.findById(stored.getId())).get()
				.extracting(StoredFile::getContent)
				.isEqualTo(content);
	}
}
