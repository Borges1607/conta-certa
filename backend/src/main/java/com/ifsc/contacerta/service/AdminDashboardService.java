package com.ifsc.contacerta.service;

import com.ifsc.contacerta.dto.admin.AdminDashboardResponse;
import com.ifsc.contacerta.model.AccountStatus;
import com.ifsc.contacerta.model.Role;
import com.ifsc.contacerta.repository.InstitutionRepository;
import com.ifsc.contacerta.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminDashboardService {
	private final InstitutionRepository institutionRepository;
	private final UserRepository userRepository;

	@Transactional(readOnly = true)
	public AdminDashboardResponse get() {
		long institutionTotal = institutionRepository.count();
		long institutionActive = institutionRepository.countByActive(true);
		long institutionInactive = institutionRepository.countByActive(false);
		long teacherTotal = userRepository.countByRole(Role.TEACHER);
		long teacherPending = userRepository.countByRoleAndStatus(Role.TEACHER, AccountStatus.PENDING);
		long teacherActive = userRepository.countByRoleAndStatus(Role.TEACHER, AccountStatus.ACTIVE);
		long teacherInactive = userRepository.countByRoleAndStatus(Role.TEACHER, AccountStatus.INACTIVE);
		return new AdminDashboardResponse(
				new AdminDashboardResponse.InstitutionCounts(institutionTotal, institutionActive, institutionInactive),
				new AdminDashboardResponse.TeacherCounts(teacherTotal, teacherPending, teacherActive, teacherInactive)
		);
	}
}
