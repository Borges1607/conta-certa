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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class RoomRepositoryTest extends PostgresIntegrationTest {

	private static final String DEF567_HASH = "dc7904f769c857873b9fc48880f556ecb93579ae3ead145d52d4326b83bbd285";
	private static final String ABC234_HASH = "8c640c4e71f90160b2b3615af86739e6b15ddc877ae79e18aada753565f756c4";

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
				"1º ano A", null, Grade.HIGH_SCHOOL_1, List.of("Porcentagem"), 50,
				"DEF567", DEF567_HASH, teacher, institution
		));
		membershipRepository.save(new RoomMembership(room, student));

		assertThat(roomRepository.findByJoinCodeHash(DEF567_HASH))
				.contains(room);
		assertThat(roomRepository.findByJoinCodeHash(new JoinCodeHasher().hash(" def567 ")))
				.contains(room);
		assertThat(roomRepository.findByJoinCodeHash("DEF567")).isEmpty();
		assertThat(roomRepository.findByTeacherIdOrderByCreatedAtDesc(
				teacher.getId(), PageRequest.of(0, 10)
		)).containsExactly(room);
		assertThat(membershipRepository.findByRoomIdAndStudentId(room.getId(), student.getId())).isPresent();
		assertThat(membershipRepository.findByRoomIdAndStatusOrderByJoinedAtAsc(
				room.getId(), MembershipStatus.ACTIVE
		)).extracting(RoomMembership::getStudent).containsExactly(student);
	}

	@Test
	void deveSubstituirDisplayEHashAoRegenerarCodigoDaSala() {
		Institution institution = institutionRepository.save(new Institution(
				"Instituto Exemplo", "11222333000181", "contato@example.com", "48999990000", true
		));
		User teacher = userRepository.save(new User(
				Role.TEACHER, AccountStatus.ACTIVE, "Professora Ana", "ana3@example.com", "PROF-3", institution
		));
		Room room = roomRepository.saveAndFlush(new Room(
				"1º ano A", null, Grade.HIGH_SCHOOL_1, List.of("Porcentagem"), 50,
				"DEF567", DEF567_HASH, teacher, institution
		));

		room.changeJoinCode("ABC234", ABC234_HASH);
		roomRepository.saveAndFlush(room);

		assertThat(roomRepository.findByJoinCodeHash(DEF567_HASH)).isEmpty();
		assertThat(roomRepository.findByJoinCodeHash(ABC234_HASH))
				.get()
				.satisfies(regenerated -> {
					assertThat(regenerated.getJoinCodeDisplay()).isEqualTo("ABC234");
				});
	}
}
