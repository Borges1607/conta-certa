package com.ifsc.contacerta.service;

import com.ifsc.contacerta.dto.extraattempt.CreateExtraAttemptGrantRequest;
import com.ifsc.contacerta.dto.extraattempt.ExtraAttemptGrantResponse;
import com.ifsc.contacerta.entity.ExtraAttemptGrant;
import com.ifsc.contacerta.entity.LessonAssignment;
import com.ifsc.contacerta.exception.ApiException;
import com.ifsc.contacerta.model.AccountStatus;
import com.ifsc.contacerta.model.MembershipStatus;
import com.ifsc.contacerta.model.Role;
import com.ifsc.contacerta.repository.AttemptRepository;
import com.ifsc.contacerta.repository.ExtraAttemptGrantRepository;
import com.ifsc.contacerta.repository.LessonAssignmentRepository;
import com.ifsc.contacerta.repository.RoomMembershipRepository;
import com.ifsc.contacerta.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExtraAttemptGrantService {
	private final UserRepository userRepository;
	private final LessonAssignmentRepository assignmentRepository;
	private final RoomMembershipRepository membershipRepository;
	private final ExtraAttemptGrantRepository grantRepository;
	private final AttemptRepository attemptRepository;
	private final Clock clock;

	@Transactional
	public ExtraAttemptGrantResponse grant(UUID teacherId, UUID assignmentId, UUID studentId, CreateExtraAttemptGrantRequest request) {
		if (request == null || request.quantity() < 1 || request.quantity() > 100) {
			throw error(
					HttpStatus.UNPROCESSABLE_CONTENT,
					"INVALID_EXTRA_ATTEMPT_QUANTITY",
					"Extra attempt quantity must be between 1 and 100."
			);
		}
		var teacher = userRepository.findById(teacherId)
				.orElseThrow(() -> error(HttpStatus.NOT_FOUND, "TEACHER_NOT_FOUND", "Teacher was not found."));
		if (teacher.getRole() != Role.TEACHER) {
			throw error(HttpStatus.FORBIDDEN, "TEACHER_REQUIRED", "A teacher account is required.");
		}
		if (teacher.getStatus() != AccountStatus.ACTIVE) {
			throw error(HttpStatus.FORBIDDEN, "ACCOUNT_INACTIVE", "Teacher account is inactive.");
		}
		LessonAssignment assignment = assignmentRepository.findById(assignmentId)
				.filter(candidate -> candidate.getRoom().getTeacher().getId().equals(teacherId))
				.orElseThrow(() -> error(HttpStatus.NOT_FOUND, "ASSIGNMENT_NOT_FOUND", "Assignment was not found."));
		if (assignment.getMaxAttempts() == null) {
			throw error(HttpStatus.CONFLICT, "UNLIMITED_ATTEMPTS", "Assignment already has unlimited attempts.");
		}
		var student = userRepository.findById(studentId)
				.orElseThrow(() -> error(HttpStatus.NOT_FOUND, "STUDENT_NOT_FOUND", "Student was not found."));
		if (student.getRole() != Role.STUDENT) {
			throw error(HttpStatus.FORBIDDEN, "STUDENT_REQUIRED", "A student account is required.");
		}
		if (student.getStatus() != AccountStatus.ACTIVE) {
			throw error(HttpStatus.FORBIDDEN, "ACCOUNT_INACTIVE", "Student account is inactive.");
		}
		var membership = membershipRepository.findForUpdateByRoomIdAndStudentId(assignment.getRoom().getId(), studentId)
				.filter(candidate -> candidate.getStatus() == MembershipStatus.ACTIVE)
				.orElseThrow(() -> error(HttpStatus.NOT_FOUND, "MEMBERSHIP_NOT_FOUND", "Membership was not found."));
		ExtraAttemptGrant grant = grantRepository.save(new ExtraAttemptGrant(assignment, student, teacher, request.quantity(), Instant.now(clock)));
		long granted = grantRepository.sumQuantityByAssignmentIdAndStudentId(assignmentId, studentId);
		long used = attemptRepository.countByAssignmentIdAndStudentId(assignmentId, studentId);
		return new ExtraAttemptGrantResponse(grant.getId(), Math.toIntExact(granted), used, Math.max(0, assignment.getMaxAttempts() + granted - used));
	}

	private ApiException error(HttpStatus status, String code, String message) {
		return new ApiException(status, code, message);
	}
}
