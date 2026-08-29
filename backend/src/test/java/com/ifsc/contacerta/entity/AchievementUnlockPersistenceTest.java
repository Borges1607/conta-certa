package com.ifsc.contacerta.entity;

import com.ifsc.contacerta.model.AccountStatus;
import com.ifsc.contacerta.model.AchievementCode;
import com.ifsc.contacerta.model.Grade;
import com.ifsc.contacerta.model.Role;
import com.ifsc.contacerta.repository.AchievementUnlockRepository;
import com.ifsc.contacerta.repository.InstitutionRepository;
import com.ifsc.contacerta.repository.UserRepository;
import com.ifsc.contacerta.support.PostgresIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class AchievementUnlockPersistenceTest extends PostgresIntegrationTest {

	private static final Instant UNLOCKED_AT = Instant.parse("2026-08-29T12:00:00Z");

	@Autowired private InstitutionRepository institutionRepository;
	@Autowired private UserRepository userRepository;
	@Autowired private AchievementUnlockRepository unlockRepository;
	@Autowired private EntityManager entityManager;

	@Test
	void deveInserirDesbloqueioUmaUnicaVez() {
		Fixture fixture = fixture();

		int first = unlockRepository.insertIfAbsent(
				UUID.randomUUID(), fixture.room().getId(), fixture.student().getId(),
				AchievementCode.FIRST_PASS.name(), UNLOCKED_AT
		);
		int repeated = unlockRepository.insertIfAbsent(
				UUID.randomUUID(), fixture.room().getId(), fixture.student().getId(),
				AchievementCode.FIRST_PASS.name(), UNLOCKED_AT.plusSeconds(30)
		);
		entityManager.flush();

		assertThat(first).isEqualTo(1);
		assertThat(repeated).isZero();
		assertThat(unlockRepository.findByRoomIdAndStudentId(fixture.room().getId(), fixture.student().getId()))
				.singleElement()
				.satisfies(unlock -> {
					assertThat(unlock.getCode()).isEqualTo(AchievementCode.FIRST_PASS);
					assertThat(unlock.getUnlockedAt()).isEqualTo(UNLOCKED_AT);
				});
	}

	private Fixture fixture() {
		Institution institution = institutionRepository.save(new Institution(
				"Instituto", "11222333000181", "contato@example.com", "48999990000", true
		));
		User teacher = userRepository.save(new User(
				Role.TEACHER, AccountStatus.ACTIVE, "Professora", "prof@example.com", "P-1", institution
		));
		User student = userRepository.save(new User(
				Role.STUDENT, AccountStatus.ACTIVE, "Aluno", "aluno@example.com", "A-1", institution
		));
		Room room = new Room(
				"Sala", null, Grade.HIGH_SCHOOL_1, List.of("Porcentagem"), 50,
				"ABC234", "hash", teacher, institution
		);
		entityManager.persist(room);
		entityManager.flush();
		return new Fixture(room, student);
	}

	private record Fixture(Room room, User student) {}
}
