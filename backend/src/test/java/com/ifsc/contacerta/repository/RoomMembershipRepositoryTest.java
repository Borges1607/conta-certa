package com.ifsc.contacerta.repository;

import com.ifsc.contacerta.entity.Institution;
import com.ifsc.contacerta.entity.Room;
import com.ifsc.contacerta.entity.RoomMembership;
import com.ifsc.contacerta.entity.User;
import com.ifsc.contacerta.model.AccountStatus;
import com.ifsc.contacerta.model.Grade;
import com.ifsc.contacerta.model.MembershipStatus;
import com.ifsc.contacerta.model.Role;
import com.ifsc.contacerta.service.JoinCodeHasher;
import com.ifsc.contacerta.support.PostgresIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class RoomMembershipRepositoryTest extends PostgresIntegrationTest {

	@Autowired private EntityManager entityManager;
	@Autowired private InstitutionRepository institutionRepository;
	@Autowired private UserRepository userRepository;
	@Autowired private RoomRepository roomRepository;
	@Autowired private RoomMembershipRepository membershipRepository;

	@Test
	void deveProjetarMatriculasAtivasEmOrdemDecrescenteDeIngresso() {
		Institution institution = institutionRepository.save(new Institution(
				"Instituto Exemplo", "11222333000181", "contato@example.com", "48999990000", true
		));
		User teacher = userRepository.save(user(Role.TEACHER, "Professora Ana", "ana@example.com", institution));
		User olderStudent = userRepository.save(user(Role.STUDENT, "Aluno Bruno", "bruno@example.com", institution));
		User newerStudent = userRepository.save(user(Role.STUDENT, "Aluna Carla", "carla@example.com", institution));
		Room room = roomRepository.save(new Room(
				"1º ano A", null, Grade.HIGH_SCHOOL_1, List.of("Porcentagem"), 50,
				"ABC123", new JoinCodeHasher().hash("ABC123"), teacher, institution
		));
		RoomMembership olderMembership = membershipRepository.save(new RoomMembership(room, olderStudent));
		RoomMembership newerMembership = membershipRepository.save(new RoomMembership(room, newerStudent));
		entityManager.flush();
		entityManager.createNativeQuery("update room_memberships set joined_at = :joinedAt where id = :membershipId")
				.setParameter("joinedAt", Instant.parse("2026-01-01T00:00:00Z"))
				.setParameter("membershipId", olderMembership.getId())
				.executeUpdate();
		entityManager.createNativeQuery("update room_memberships set joined_at = :joinedAt where id = :membershipId")
				.setParameter("joinedAt", Instant.parse("2026-02-01T00:00:00Z"))
				.setParameter("membershipId", newerMembership.getId())
				.executeUpdate();
		entityManager.clear();

		var result = membershipRepository.findStudentResponsesByRoomIdAndStatusOrderByJoinedAtDesc(
				room.getId(), MembershipStatus.ACTIVE, PageRequest.of(0, 10)
		);

		assertThat(result.getContent())
				.extracting(response -> response.studentId())
				.containsExactly(newerStudent.getId(), olderStudent.getId());
		assertThat(result.getContent())
				.allSatisfy(response -> {
					assertThat(response.xp()).isZero();
					assertThat(response.completedLessons()).isZero();
					assertThat(response.totalLessons()).isZero();
					assertThat(response.stars()).isZero();
					assertThat(response.lastActivityAt()).isNull();
					assertThat(response.membershipStatus()).isEqualTo(MembershipStatus.ACTIVE);
				});
	}

	private User user(Role role, String name, String email, Institution institution) {
		return new User(role, AccountStatus.ACTIVE, name, email, role == Role.TEACHER ? "PROF-1" : "ALUNO-1", institution);
	}
}
