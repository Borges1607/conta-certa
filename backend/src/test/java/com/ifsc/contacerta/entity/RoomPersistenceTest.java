package com.ifsc.contacerta.entity;

import com.ifsc.contacerta.model.AccountStatus;
import com.ifsc.contacerta.model.Grade;
import com.ifsc.contacerta.model.MembershipStatus;
import com.ifsc.contacerta.model.Role;
import com.ifsc.contacerta.repository.InstitutionRepository;
import com.ifsc.contacerta.repository.UserRepository;
import com.ifsc.contacerta.support.PostgresIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class RoomPersistenceTest extends PostgresIntegrationTest {

	@Autowired
	private InstitutionRepository institutionRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private EntityManager entityManager;

	@Test
	void devePersistirSalaComProfessorTopicosEMatriculaAtiva() {
		Institution institution = institutionRepository.save(new Institution(
				"Instituto Exemplo", "11222333000181", "contato@example.com", "48999990000", true
		));
		User teacher = userRepository.save(new User(
				Role.TEACHER, AccountStatus.ACTIVE, "Professora Ana", "ana@example.com", "PROF-1", institution
		));
		User student = userRepository.save(new User(
				Role.STUDENT, AccountStatus.ACTIVE, "Aluno Bruno", "bruno@example.com", "ALUNO-1", institution
		));
		Room room = new Room(
				"2º ano A",
				"Matemática financeira aplicada",
				Grade.HIGH_SCHOOL_2,
				List.of("Porcentagem", "Juros compostos"),
				50,
				"ABC234",
				teacher,
				institution
		);
		entityManager.persist(room);
		RoomMembership membership = new RoomMembership(room, student);
		entityManager.persist(membership);
		entityManager.flush();
		entityManager.clear();

		Room persistedRoom = entityManager.find(Room.class, room.getId());
		RoomMembership persistedMembership = entityManager.find(RoomMembership.class, membership.getId());

		assertThat(persistedRoom.getTeacher().getId()).isEqualTo(teacher.getId());
		assertThat(persistedRoom.getInstitution().getId()).isEqualTo(institution.getId());
		assertThat(persistedRoom.getGrade()).isEqualTo(Grade.HIGH_SCHOOL_2);
		assertThat(persistedRoom.getContentTopics()).containsExactly("Porcentagem", "Juros compostos");
		assertThat(persistedMembership.getStatus()).isEqualTo(MembershipStatus.ACTIVE);
	}
}
