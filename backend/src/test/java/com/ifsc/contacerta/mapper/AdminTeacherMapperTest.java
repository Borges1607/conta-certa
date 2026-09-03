package com.ifsc.contacerta.mapper;

import com.ifsc.contacerta.entity.Institution;
import com.ifsc.contacerta.entity.User;
import com.ifsc.contacerta.model.AccountStatus;
import com.ifsc.contacerta.model.Role;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AdminTeacherMapperTest {

	@Test
	void mapeiaProfessorComInstituicaoEUltimoLogin() {
		Institution institution = new Institution("Alfa", "12345678000195", "a@example.com", "+5548999999999", true);
		User teacher = new User(Role.TEACHER, AccountStatus.ACTIVE, "Ana Souza", "ANA@EXAMPLE.COM", "MAT-1", institution);
		Instant lastLogin = Instant.parse("2026-09-03T12:00:00Z");

		var response = AdminTeacherMapper.toResponse(teacher, lastLogin);

		assertThat(response.id()).isEqualTo(teacher.getId());
		assertThat(response.fullName()).isEqualTo("Ana Souza");
		assertThat(response.email()).isEqualTo("ANA@EXAMPLE.COM");
		assertThat(response.registrationNumber()).isEqualTo("MAT-1");
		assertThat(response.institution().name()).isEqualTo("Alfa");
		assertThat(response.status()).isEqualTo(AccountStatus.ACTIVE);
		assertThat(response.emailVerified()).isFalse();
		assertThat(response.lastLoginAt()).isEqualTo(lastLogin);
	}
}
