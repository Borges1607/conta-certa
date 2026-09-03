package com.ifsc.contacerta.service;

import com.ifsc.contacerta.dto.admin.AdminTeacherResponse;
import com.ifsc.contacerta.dto.admin.CreateTeacherRequest;
import com.ifsc.contacerta.dto.admin.PatchTeacherRequest;
import com.ifsc.contacerta.entity.Institution;
import com.ifsc.contacerta.entity.User;
import com.ifsc.contacerta.exception.ApiException;
import com.ifsc.contacerta.mapper.AdminTeacherMapper;
import com.ifsc.contacerta.model.AccountStatus;
import com.ifsc.contacerta.model.Role;
import com.ifsc.contacerta.repository.AdminHistoryQueryRepository;
import com.ifsc.contacerta.repository.AuthSessionRepository;
import com.ifsc.contacerta.repository.InstitutionRepository;
import com.ifsc.contacerta.repository.UserRepository;
import com.ifsc.contacerta.specification.TeacherSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.time.Clock;

@Service
@RequiredArgsConstructor
public class AdminTeacherService {
	private final UserRepository userRepository;
	private final InstitutionRepository institutionRepository;
	private final AuthSessionRepository authSessionRepository;
	private final AdminHistoryQueryRepository historyRepository;
	private final AccountLifecycleService accountLifecycleService;
	private final Clock clock;

	@Transactional(readOnly = true)
	public Page<AdminTeacherResponse> list(String search, AccountStatus status, UUID institutionId, Pageable pageable) {
		return userRepository.findAll(TeacherSpecification.filtered(search, status, institutionId), pageable)
				.map(this::toResponse);
	}

	@Transactional(readOnly = true)
	public AdminTeacherResponse get(UUID id) {
		return toResponse(findTeacher(id));
	}

	@Transactional
	public User create(CreateTeacherRequest request) {
		String email = request.email().trim().toLowerCase(java.util.Locale.ROOT);
		if (userRepository.existsByEmailIgnoreCase(email)) {
			throw new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED", "Email is already registered.");
		}
		Institution institution = findActiveInstitution(request.institutionId());
		User teacher = new User(Role.TEACHER, AccountStatus.PENDING, request.fullName().trim(), email,
				request.registrationNumber().trim(), institution);
		userRepository.save(teacher);
		accountLifecycleService.inviteTeacher(teacher);
		return teacher;
	}

	@Transactional
	public AdminTeacherResponse update(UUID id, PatchTeacherRequest request) {
		User teacher = findTeacher(id);
		checkVersion(teacher, request.version());
		Institution institution = findActiveInstitution(request.institutionId());
		UUID currentInstitutionId = teacher.getInstitution() == null ? null : teacher.getInstitution().getId();
		if (!institution.getId().equals(currentInstitutionId) && historyRepository.hasTeacherHistory(id)) {
			throw new ApiException(HttpStatus.CONFLICT, "TEACHER_INSTITUTION_CHANGE_BLOCKED", "Teacher institution cannot be changed after history exists.");
		}
		teacher.updateTeacherProfile(request.fullName().trim(), request.registrationNumber().trim(), institution);
		return toResponse(teacher);
	}

	@Transactional
	public AdminTeacherResponse activate(UUID id) {
		User teacher = findTeacher(id);
		if (teacher.getStatus() != AccountStatus.INACTIVE) {
			throw new ApiException(HttpStatus.CONFLICT, "TEACHER_STATUS_TRANSITION_INVALID", "Only an inactive teacher can be activated.");
		}
		teacher.activate();
		return toResponse(teacher);
	}

	@Transactional
	public AdminTeacherResponse deactivate(UUID id) {
		User teacher = findTeacher(id);
		if (teacher.getStatus() == AccountStatus.INACTIVE) {
			return toResponse(teacher);
		}
		teacher.deactivate();
		authSessionRepository.revokeAllActiveByUserId(id, clock.instant());
		return toResponse(teacher);
	}

	@Transactional
	public void passwordReset(UUID id) {
		User teacher = findTeacher(id);
		accountLifecycleService.forgotPassword(teacher.getEmail());
	}

	private AdminTeacherResponse toResponse(User teacher) {
		return AdminTeacherMapper.toResponse(teacher, authSessionRepository.findLastUsedAtByUserId(teacher.getId()).orElse(null));
	}

	private User findTeacher(UUID id) {
		return userRepository.findByIdAndRole(id, Role.TEACHER)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TEACHER_NOT_FOUND", "Teacher was not found."));
	}

	private Institution findActiveInstitution(UUID id) {
		return institutionRepository.findById(id).filter(Institution::isActive)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "INSTITUTION_NOT_FOUND", "Institution was not found."));
	}

	private void checkVersion(User teacher, long expected) {
		if (teacher.getVersion() != expected) {
			throw new ApiException(HttpStatus.CONFLICT, "VERSION_CONFLICT", "The teacher was changed by another request.");
		}
	}
}
