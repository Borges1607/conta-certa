package com.ifsc.contacerta.repository;

import com.ifsc.contacerta.entity.Institution;
import com.ifsc.contacerta.entity.Room;
import com.ifsc.contacerta.entity.RoomMembership;
import com.ifsc.contacerta.entity.User;
import com.ifsc.contacerta.model.AccountStatus;
import com.ifsc.contacerta.model.Grade;
import com.ifsc.contacerta.model.MembershipStatus;
import com.ifsc.contacerta.model.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class RoomRepositoryTest {

	@Autowired private InstitutionRepository institutionRepository;
	@Autowired private UserRepository userRepository;
	@Autowired private RoomRepository roomRepository;
	@Autowired private RoomMembershipRepository membershipRepository;

	@Test
	void deveConsultarSalaEMatriculaPelosFiltrosDoDominio() {
		Institution institution = institutionRepository.save(new Institution(
				"Instituto Exemplo", "11222333000181", "contato@example.com", "48999990000", true
		));
		User teacher = userRepository.save(new User(
				Role.TEACHER, AccountStatus.ACTIVE, "Professora Ana", "ana2@example.com", "PROF-2", institution
		));
		User student = userRepository.save(new User(
				Role.STUDENT, AccountStatus.ACTIVE, "Aluno Bruno", "bruno2@example.com", "ALUNO-2", institution
		));
		Room room = roomRepository.save(new Room(
				"1º ano A", null, Grade.HIGH_SCHOOL_1, List.of("Porcentagem"), 50, "DEF567", teacher, institution
		));
		membershipRepository.save(new RoomMembership(room, student));

		assertThat(roomRepository.findByJoinCode("def567")).contains(room);
		assertThat(roomRepository.findByTeacherIdOrderByCreatedAtDesc(
				teacher.getId(), PageRequest.of(0, 10)
		)).containsExactly(room);
		assertThat(membershipRepository.findByRoomIdAndStudentId(room.getId(), student.getId())).isPresent();
		assertThat(membershipRepository.findByRoomIdAndStatusOrderByJoinedAtAsc(
				room.getId(), MembershipStatus.ACTIVE
		)).extracting(RoomMembership::getStudent).containsExactly(student);
	}
}
