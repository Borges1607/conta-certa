package com.ifsc.contacerta.repository;

import com.ifsc.contacerta.entity.AuthSession;
import com.ifsc.contacerta.entity.Institution;
import com.ifsc.contacerta.entity.User;
import com.ifsc.contacerta.model.AccountStatus;
import com.ifsc.contacerta.model.Role;
import com.ifsc.contacerta.specification.TeacherSpecification;
import com.ifsc.contacerta.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class AdminTeacherRepositoryTest extends PostgresIntegrationTest {

	@Autowired private UserRepository userRepository;
	@Autowired private InstitutionRepository institutionRepository;
	@Autowired private AuthSessionRepository authSessionRepository;

	@Test
	void filtraApenasProfessoresPorBuscaStatusEInstituicao() {
		Institution institution = institutionRepository.save(new Institution("Alfa", "12345678000195", "a@example.com", "+5548999999999", true));
		Institution other = institutionRepository.save(new Institution("Beta", "98765432000110", "b@example.com", "+5548888888888", true));
		User ana = userRepository.save(new User(Role.TEACHER, AccountStatus.ACTIVE, "Ana Souza", "ana@example.com", "MAT-1", institution));
		userRepository.save(new User(Role.TEACHER, AccountStatus.PENDING, "Bruno Lima", "bruno@example.com", "MAT-2", institution));
		userRepository.save(new User(Role.STUDENT, AccountStatus.ACTIVE, "Ana Student", "student@example.com", "MAT-3", institution));
		userRepository.save(new User(Role.TEACHER, AccountStatus.ACTIVE, "Ana Outra", "outra@example.com", "MAT-4", other));

		assertThat(userRepository.findAll(TeacherSpecification.filtered("ana", AccountStatus.ACTIVE, institution.getId()), PageRequest.of(0, 20))
				.getContent()).containsExactly(ana);
	}

	@Test
	void encontraUltimoLoginDoProfessor() {
		Institution institution = institutionRepository.save(new Institution("Alfa", "12345678000195", "a@example.com", "+5548999999999", true));
		User teacher = userRepository.save(new User(Role.TEACHER, AccountStatus.ACTIVE, "Ana Souza", "ana@example.com", "MAT-1", institution));
		Instant first = Instant.parse("2026-09-03T10:00:00Z");
		Instant last = Instant.parse("2026-09-03T12:00:00Z");
		authSessionRepository.save(new AuthSession(teacher, last.plusSeconds(3600), first));
		authSessionRepository.save(new AuthSession(teacher, last.plusSeconds(3600), last));

		assertThat(authSessionRepository.findLastUsedAtByUserId(teacher.getId())).contains(last);
	}
}
