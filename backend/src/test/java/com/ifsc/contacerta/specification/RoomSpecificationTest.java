package com.ifsc.contacerta.specification;

import com.ifsc.contacerta.entity.Institution;
import com.ifsc.contacerta.entity.Room;
import com.ifsc.contacerta.entity.User;
import com.ifsc.contacerta.model.AccountStatus;
import com.ifsc.contacerta.model.Grade;
import com.ifsc.contacerta.model.Role;
import com.ifsc.contacerta.repository.InstitutionRepository;
import com.ifsc.contacerta.repository.RoomRepository;
import com.ifsc.contacerta.repository.UserRepository;
import com.ifsc.contacerta.service.JoinCodeHasher;
import com.ifsc.contacerta.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class RoomSpecificationTest extends PostgresIntegrationTest {

	@Autowired private InstitutionRepository institutionRepository;
	@Autowired private UserRepository userRepository;
	@Autowired private RoomRepository roomRepository;

	@Test
	void deveRestringirAoProfessorEAceitarBuscaSemDiferenciarMaiusculasQuandoSalaAtiva() {
		Institution institution = institutionRepository.save(institution());
		User teacher = userRepository.save(teacher("ana@example.com", institution));
		User anotherTeacher = userRepository.save(teacher("bia@example.com", institution));
		Room matchingRoom = roomRepository.save(room("Matemática Financeira", "AAA111", teacher, institution));
		Room archivedRoom = roomRepository.save(room("Matemática Arquivada", "BBB222", teacher, institution));
		archivedRoom.archive();
		roomRepository.save(archivedRoom);
		roomRepository.save(room("Matemática Financeira", "CCC333", anotherTeacher, institution));

		var result = roomRepository.findAll(
				RoomSpecification.ownedBy(teacher.getId(), "fInAnCeIrA", false),
				PageRequest.of(0, 10)
		);

		assertThat(result.getContent()).containsExactly(matchingRoom);
	}

	@Test
	void deveIgnorarBuscaEmBrancoEFiltrarSalasArquivadasSomenteQuandoSolicitado() {
		Institution institution = institutionRepository.save(institution());
		User teacher = userRepository.save(teacher("carla@example.com", institution));
		roomRepository.save(room("Sala ativa", "DDD444", teacher, institution));
		Room archivedRoom = roomRepository.save(room("Sala arquivada", "EEE555", teacher, institution));
		archivedRoom.archive();
		roomRepository.save(archivedRoom);

		var allRooms = roomRepository.findAll(
				RoomSpecification.ownedBy(teacher.getId(), "   ", null),
				PageRequest.of(0, 10)
		);
		var archivedRooms = roomRepository.findAll(
				RoomSpecification.ownedBy(teacher.getId(), null, true),
				PageRequest.of(0, 10)
		);

		assertThat(allRooms).hasSize(2);
		assertThat(archivedRooms).extracting(Room::getId).containsExactly(archivedRoom.getId());
	}

	private Institution institution() {
		return new Institution("Instituto Exemplo", "11222333000181", "contato@example.com", "48999990000", true);
	}

	private User teacher(String email, Institution institution) {
		return new User(Role.TEACHER, AccountStatus.ACTIVE, "Professora Ana", email, "PROF-1", institution);
	}

	private Room room(String name, String joinCode, User teacher, Institution institution) {
		return new Room(
				name, null, Grade.HIGH_SCHOOL_1, List.of("Porcentagem"), 50,
				joinCode, new JoinCodeHasher().hash(joinCode), teacher, institution
		);
	}
}
