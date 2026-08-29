package com.ifsc.contacerta.service;

import com.ifsc.contacerta.entity.Institution;
import com.ifsc.contacerta.entity.Room;
import com.ifsc.contacerta.entity.RoomStudentProgress;
import com.ifsc.contacerta.entity.User;
import com.ifsc.contacerta.model.AccountStatus;
import com.ifsc.contacerta.model.AchievementCode;
import com.ifsc.contacerta.model.Grade;
import com.ifsc.contacerta.model.Role;
import com.ifsc.contacerta.repository.AchievementUnlockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class AchievementUnlockServiceTest {

	private static final Instant FINALIZED_AT = Instant.parse("2026-08-29T12:00:00Z");

	private AchievementUnlockRepository repository;
	private AchievementUnlockService service;

	@BeforeEach
	void setUp() {
		repository = mock(AchievementUnlockRepository.class);
		service = new AchievementUnlockService(repository);
	}

	@Test
	void deveDesbloquearConquistasAtingidas() {
		RoomStudentProgress progress = progress(500, 5);

		service.evaluate(progress, 100, FINALIZED_AT);

		verifyUnlocked(progress, AchievementCode.FIRST_PASS);
		verifyUnlocked(progress, AchievementCode.PERFECT_SCORE);
		verifyUnlocked(progress, AchievementCode.XP_100);
		verifyUnlocked(progress, AchievementCode.XP_500);
		verifyNotUnlocked(progress, AchievementCode.XP_1000);
		verifyUnlocked(progress, AchievementCode.PASSED_5);
		verifyNotUnlocked(progress, AchievementCode.PASSED_10);
	}

	@Test
	void deveRespeitarLimitesInferiores() {
		RoomStudentProgress progress = progress(99, 4);

		service.evaluate(progress, 99, FINALIZED_AT);

		verifyUnlocked(progress, AchievementCode.FIRST_PASS);
		verifyNotUnlocked(progress, AchievementCode.PERFECT_SCORE);
		verifyNotUnlocked(progress, AchievementCode.XP_100);
		verifyNotUnlocked(progress, AchievementCode.XP_500);
		verifyNotUnlocked(progress, AchievementCode.XP_1000);
		verifyNotUnlocked(progress, AchievementCode.PASSED_5);
		verifyNotUnlocked(progress, AchievementCode.PASSED_10);
	}

	@Test
	void deveDesbloquearMilXpEDezAprovacoesNosLimitesExatos() {
		RoomStudentProgress progress = progress(1_000, 10);

		service.evaluate(progress, 80, FINALIZED_AT);

		verifyUnlocked(progress, AchievementCode.XP_1000);
		verifyUnlocked(progress, AchievementCode.PASSED_10);
	}

	@Test
	void naoDeveDesbloquearPrimeiraAprovacaoSemAprovacoes() {
		RoomStudentProgress progress = progress(0, 0);

		service.evaluate(progress, 0, FINALIZED_AT);

		verifyNotUnlocked(progress, AchievementCode.FIRST_PASS);
	}

	private RoomStudentProgress progress(int totalXp, int passedCount) {
		Institution institution = new Institution(
				"Instituto", "11222333000181", "contato@example.com", "48999990000", true
		);
		User teacher = new User(
				Role.TEACHER, AccountStatus.ACTIVE, "Professora", "prof@example.com", "P-1", institution
		);
		User student = new User(
				Role.STUDENT, AccountStatus.ACTIVE, "Aluno", "aluno@example.com", "A-1", institution
		);
		Room room = new Room(
				"Sala", null, Grade.HIGH_SCHOOL_1, List.of("Porcentagem"), 50,
				"ABC234", "hash", teacher, institution
		);
		RoomStudentProgress progress = new RoomStudentProgress(room, student);
		progress.applyResult(totalXp, 0, false, false, FINALIZED_AT);
		for (int index = 0; index < passedCount; index++) {
			progress.applyResult(0, 0, false, true, FINALIZED_AT);
		}
		return progress;
	}

	private void verifyUnlocked(RoomStudentProgress progress, AchievementCode code) {
		verify(repository).insertIfAbsent(
				any(), eq(progress.getRoom().getId()), eq(progress.getStudent().getId()),
				eq(code.name()), eq(FINALIZED_AT)
		);
	}

	private void verifyNotUnlocked(RoomStudentProgress progress, AchievementCode code) {
		verify(repository, never()).insertIfAbsent(
				any(), eq(progress.getRoom().getId()), eq(progress.getStudent().getId()),
				eq(code.name()), any()
		);
	}
}
