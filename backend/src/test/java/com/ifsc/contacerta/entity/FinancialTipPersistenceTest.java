package com.ifsc.contacerta.entity;

import com.ifsc.contacerta.support.PostgresIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
class FinancialTipPersistenceTest extends PostgresIntegrationTest {

	@Autowired
	private EntityManager entityManager;

	@Test
	void persisteDataSemFusoEArquivaDicaLogicamente() {
		FinancialTip tip = new FinancialTip("Poupança", "Use **metas**.", "https://example.com/fonte", LocalDate.of(2026, 9, 3), true);
		entityManager.persist(tip);
		entityManager.flush();
		entityManager.clear();

		FinancialTip persisted = entityManager.find(FinancialTip.class, tip.getId());
		assertThat(persisted.getPublicationDate()).isEqualTo(LocalDate.of(2026, 9, 3));
		assertThat(persisted.getArchivedAt()).isNull();

		persisted.archive(Instant.parse("2026-09-03T12:00:00Z"));
		assertThat(persisted.isActive()).isFalse();
		assertThat(persisted.getArchivedAt()).isEqualTo(Instant.parse("2026-09-03T12:00:00Z"));
		assertThatThrownBy(() -> persisted.update("Novo", "conteúdo", null, LocalDate.of(2026, 9, 4)))
				.isInstanceOf(IllegalStateException.class);
	}
}
