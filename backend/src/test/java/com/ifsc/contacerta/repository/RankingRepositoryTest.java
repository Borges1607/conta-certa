package com.ifsc.contacerta.repository;

import com.ifsc.contacerta.entity.Institution;
import com.ifsc.contacerta.entity.Room;
import com.ifsc.contacerta.entity.RoomMembership;
import com.ifsc.contacerta.entity.RoomStudentProgress;
import com.ifsc.contacerta.entity.User;
import com.ifsc.contacerta.model.AccountStatus;
import com.ifsc.contacerta.model.Grade;
import com.ifsc.contacerta.model.Role;
import com.ifsc.contacerta.support.PostgresIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class RankingRepositoryTest extends PostgresIntegrationTest {

	private static final Instant NOW = Instant.parse("2026-08-29T12:00:00Z");

	@Autowired private InstitutionRepository institutionRepository;
	@Autowired private UserRepository userRepository;
	@Autowired private RankingRepository rankingRepository;
	@Autowired private EntityManager entityManager;

	@Test
	void devePaginarOrdenarEManterPosicaoDoAluno() {
		Fixture fixture = fixture();

		Page<RankingRowProjection> firstPage = rankingRepository.findPage(
				fixture.room().getId(), PageRequest.of(0, 2)
		);

		assertThat(firstPage.getTotalElements()).isEqualTo(3);
		assertThat(firstPage.getTotalPages()).isEqualTo(2);
		assertThat(firstPage.getContent())
				.extracting(RankingRowProjection::getStudentId)
				.containsExactly(fixture.leading().getId(), fixture.second().getId());
		assertThat(firstPage.getContent())
				.extracting(RankingRowProjection::getPosition)
				.containsExactly(1L, 2L);

		RankingRowProjection self = rankingRepository.findStudent(
				fixture.room().getId(), fixture.zeroProgress().getId()
		).orElseThrow();
		assertThat(self.getPosition()).isEqualTo(3);
		assertThat(self.getTotalXp()).isZero();
		assertThat(self.getTotalStars()).isZero();
		assertThat(self.getLevel()).isEqualTo(1);
	}

	@Test
	void deveExcluirMatriculaRemovidaEOutraSala() {
		Fixture fixture = fixture();

		Page<RankingRowProjection> page = rankingRepository.findPage(
				fixture.room().getId(), PageRequest.of(0, 20)
		);

		assertThat(page.getContent())
				.extracting(RankingRowProjection::getStudentId)
				.doesNotContain(fixture.removed().getId(), fixture.otherRoom().getId());
	}

	private Fixture fixture() {
		Institution institution = institutionRepository.save(new Institution(
				"Instituto", "11222333000181", "contato@example.com", "48999990000", true
		));
		User teacher = userRepository.save(user(Role.TEACHER, "Professora", "prof@example.com", "P-1", institution));
		User leading = userRepository.save(user(Role.STUDENT, "Ana Souza", "ana@example.com", "A-1", institution));
		User second = userRepository.save(user(Role.STUDENT, "Bruno Lima", "bruno@example.com", "A-2", institution));
		User zeroProgress = userRepository.save(user(Role.STUDENT, "Carla Melo", "carla@example.com", "A-3", institution));
		User removed = userRepository.save(user(Role.STUDENT, "Davi Luz", "davi@example.com", "A-4", institution));
		User otherRoom = userRepository.save(user(Role.STUDENT, "Eva Reis", "eva@example.com", "A-5", institution));
		Room room = room("Sala", "ABC234", "hash-1", teacher, institution);
		Room anotherRoom = room("Outra", "XYZ789", "hash-2", teacher, institution);
		entityManager.persist(room);
		entityManager.persist(anotherRoom);
		entityManager.persist(new RoomMembership(room, leading));
		entityManager.persist(new RoomMembership(room, second));
		entityManager.persist(new RoomMembership(room, zeroProgress));
		RoomMembership removedMembership = new RoomMembership(room, removed);
		entityManager.persist(removedMembership);
		removedMembership.remove(teacher);
		entityManager.persist(new RoomMembership(anotherRoom, otherRoom));
		entityManager.persist(progress(room, leading, 200, 5));
		entityManager.persist(progress(room, second, 200, 3));
		entityManager.persist(progress(room, removed, 1_000, 30));
		entityManager.persist(progress(anotherRoom, otherRoom, 2_000, 50));
		entityManager.flush();
		return new Fixture(room, leading, second, zeroProgress, removed, otherRoom);
	}

	private User user(Role role, String name, String email, String registration, Institution institution) {
		return new User(role, AccountStatus.ACTIVE, name, email, registration, institution);
	}

	private Room room(String name, String code, String hash, User teacher, Institution institution) {
		return new Room(name, null, Grade.HIGH_SCHOOL_1, List.of("Porcentagem"), 50, code, hash, teacher, institution);
	}

	private RoomStudentProgress progress(Room room, User student, int xp, int stars) {
		RoomStudentProgress progress = new RoomStudentProgress(room, student);
		progress.applyResult(xp, stars, false, false, NOW);
		return progress;
	}

	private record Fixture(
			Room room,
			User leading,
			User second,
			User zeroProgress,
			User removed,
			User otherRoom
	) {}
}
