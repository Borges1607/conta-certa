package com.ifsc.contacerta.service;

import com.ifsc.contacerta.dto.admin.AdminDashboardResponse;
import com.ifsc.contacerta.model.AccountStatus;
import com.ifsc.contacerta.model.Role;
import com.ifsc.contacerta.repository.InstitutionRepository;
import com.ifsc.contacerta.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminDashboardServiceTest {
	@Mock InstitutionRepository institutionRepository;
	@Mock UserRepository userRepository;
	@InjectMocks AdminDashboardService service;

	@Test
	void agregaContagensPorStatus() {
		when(institutionRepository.count()).thenReturn(10L);
		when(institutionRepository.countByActive(true)).thenReturn(8L);
		when(institutionRepository.countByActive(false)).thenReturn(2L);
		when(userRepository.countByRole(Role.TEACHER)).thenReturn(25L);
		when(userRepository.countByRoleAndStatus(Role.TEACHER, AccountStatus.PENDING)).thenReturn(3L);
		when(userRepository.countByRoleAndStatus(Role.TEACHER, AccountStatus.ACTIVE)).thenReturn(20L);
		when(userRepository.countByRoleAndStatus(Role.TEACHER, AccountStatus.INACTIVE)).thenReturn(2L);

		assertThat(service.get()).isEqualTo(new AdminDashboardResponse(
				new AdminDashboardResponse.InstitutionCounts(10, 8, 2),
				new AdminDashboardResponse.TeacherCounts(25, 3, 20, 2)
		));
	}
}
