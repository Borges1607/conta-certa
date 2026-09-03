package com.ifsc.contacerta.repository;

import com.ifsc.contacerta.entity.Institution;
import com.ifsc.contacerta.entity.Room;
import com.ifsc.contacerta.entity.User;
import com.ifsc.contacerta.model.AccountStatus;
import com.ifsc.contacerta.model.Grade;
import com.ifsc.contacerta.model.Role;
import com.ifsc.contacerta.service.JoinCodeHasher;
import com.ifsc.contacerta.specification.InstitutionSpecification;
import com.ifsc.contacerta.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class AdminInstitutionRepositoryTest extends PostgresIntegrationTest {

	@Autowired private InstitutionRepository institutionRepository;
	@Autowired private UserRepository userRepository;
	@Autowired private RoomRepository roomRepository;
	@Autowired private AdminHistoryQueryRepository historyQueryRepository;

	@Test
	void filtraInstituicoesPorNomeCnpjESituacao() {
		Institution active = institutionRepository.save(new Institution(
				"Instituição Alfa", "12345678000195", "alfa@example.com", "+5548999999999", true
		));
		institutionRepository.save(new Institution(
				"Instituição Beta", "98765432000110", "beta@example.com", "+5548888888888", false
		));

		assertThat(institutionRepository.findAll(
				InstitutionSpecification.filtered("123.456", true),
				PageRequest.of(0, 20, Sort.by("name")
			)).getContent()).containsExactly(active);
	}

	@Test
	void detectaHistoricoDeInstituicaoEProfessor() {
		Institution institution = institutionRepository.save(new Institution(
				"Instituição Alfa", "12345678000195", "alfa@example.com", "+5548999999999", true
		));
		User teacher = userRepository.save(new User(
				Role.TEACHER, AccountStatus.ACTIVE, "Ana", "ana@example.com", "MAT-1", institution
		));

		assertThat(historyQueryRepository.hasInstitutionHistory(institution.getId())).isTrue();
		assertThat(historyQueryRepository.hasTeacherHistory(teacher.getId())).isFalse();

		roomRepository.save(new Room(
				"Sala A", null, Grade.HIGH_SCHOOL_1, List.of("Porcentagem"), 50,
				"ABC123", new JoinCodeHasher().hash("ABC123"), teacher, institution
		));

		assertThat(historyQueryRepository.hasTeacherHistory(teacher.getId())).isTrue();
	}
}
