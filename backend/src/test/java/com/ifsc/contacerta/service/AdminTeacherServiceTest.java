package com.ifsc.contacerta.service;

import com.ifsc.contacerta.dto.admin.CreateTeacherRequest;
import com.ifsc.contacerta.dto.admin.PatchTeacherRequest;
import com.ifsc.contacerta.entity.Institution;
import com.ifsc.contacerta.entity.User;
import com.ifsc.contacerta.exception.ApiException;
import com.ifsc.contacerta.model.AccountStatus;
import com.ifsc.contacerta.model.Role;
import com.ifsc.contacerta.repository.AdminHistoryQueryRepository;
import com.ifsc.contacerta.repository.AuthSessionRepository;
import com.ifsc.contacerta.repository.InstitutionRepository;
import com.ifsc.contacerta.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminTeacherServiceTest {

	@Mock UserRepository userRepository;
	@Mock InstitutionRepository institutionRepository;
	@Mock AuthSessionRepository authSessionRepository;
	@Mock AdminHistoryQueryRepository historyRepository;
	@Mock AccountLifecycleService accountLifecycleService;
	@Mock Clock clock;
	@InjectMocks AdminTeacherService service;

	@Test
	void criaProfessorPendenteEEnviaConvite() {
		Institution institution = new Institution("Alfa", "12345678000195", "a@example.com", "+5548999999999", true);
		when(institutionRepository.findById(any())).thenReturn(Optional.of(institution));
		when(userRepository.existsByEmailIgnoreCase("ana@example.com")).thenReturn(false);
		when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

		User created = service.create(new CreateTeacherRequest(" Ana Souza ", "ANA@EXAMPLE.COM", " MAT-1 ", institution.getId()));

		assertThat(created.getRole()).isEqualTo(Role.TEACHER);
		assertThat(created.getStatus()).isEqualTo(AccountStatus.PENDING);
		assertThat(created.getEmail()).isEqualTo("ana@example.com");
		verify(accountLifecycleService).inviteTeacher(created);
	}

	@Test
	void rejeitaVersaoObsoletaAoEditar() {
		User teacher = new User(Role.TEACHER, AccountStatus.ACTIVE, "Ana", "ana@example.com", "MAT-1", null);
		when(userRepository.findByIdAndRole(any(), eq(Role.TEACHER))).thenReturn(Optional.of(teacher));
		assertThatThrownBy(() -> service.update(teacher.getId(), new PatchTeacherRequest("Ana", "MAT-2", null, 99L)))
				.isInstanceOf(ApiException.class)
				.hasFieldOrPropertyWithValue("code", "VERSION_CONFLICT");
		verify(userRepository, never()).save(any());
	}

	@Test
	void bloqueiaMudancaDeInstituicaoComHistorico() {
		Institution first = new Institution("Alfa", "12345678000195", "a@example.com", "+5548999999999", true);
		Institution second = new Institution("Beta", "98765432000110", "b@example.com", "+5548888888888", true);
		User teacher = new User(Role.TEACHER, AccountStatus.ACTIVE, "Ana", "ana@example.com", "MAT-1", first);
		when(userRepository.findByIdAndRole(any(), eq(Role.TEACHER))).thenReturn(Optional.of(teacher));
		when(institutionRepository.findById(second.getId())).thenReturn(Optional.of(second));
		when(historyRepository.hasTeacherHistory(teacher.getId())).thenReturn(true);

		assertThatThrownBy(() -> service.update(teacher.getId(), new PatchTeacherRequest("Ana", "MAT-2", second.getId(), 0L)))
				.isInstanceOf(ApiException.class)
				.hasFieldOrPropertyWithValue("code", "TEACHER_INSTITUTION_CHANGE_BLOCKED");
	}

	@Test
	void ativaSomenteProfessorInativo() {
		User teacher = new User(Role.TEACHER, AccountStatus.INACTIVE, "Ana", "ana@example.com", "MAT-1", null);
		when(userRepository.findByIdAndRole(teacher.getId(), Role.TEACHER)).thenReturn(Optional.of(teacher));
		when(authSessionRepository.findLastUsedAtByUserId(teacher.getId())).thenReturn(Optional.empty());

		service.activate(teacher.getId());

		assertThat(teacher.getStatus()).isEqualTo(AccountStatus.ACTIVE);
	}

	@Test
	void desativaProfessorRevogandoSessoes() {
		User teacher = new User(Role.TEACHER, AccountStatus.ACTIVE, "Ana", "ana@example.com", "MAT-1", null);
		when(userRepository.findByIdAndRole(teacher.getId(), Role.TEACHER)).thenReturn(Optional.of(teacher));
		when(authSessionRepository.findLastUsedAtByUserId(teacher.getId())).thenReturn(Optional.empty());
		when(clock.instant()).thenReturn(java.time.Instant.parse("2026-09-03T12:00:00Z"));

		service.deactivate(teacher.getId());

		assertThat(teacher.getStatus()).isEqualTo(AccountStatus.INACTIVE);
		verify(authSessionRepository).revokeAllActiveByUserId(teacher.getId(), java.time.Instant.parse("2026-09-03T12:00:00Z"));
	}

	@Test
	void solicitaRedefinicaoSemExporSenha() {
		User teacher = new User(Role.TEACHER, AccountStatus.ACTIVE, "Ana", "ana@example.com", "MAT-1", null);
		when(userRepository.findByIdAndRole(teacher.getId(), Role.TEACHER)).thenReturn(Optional.of(teacher));

		service.passwordReset(teacher.getId());

		verify(accountLifecycleService).forgotPassword("ana@example.com");
	}
}
