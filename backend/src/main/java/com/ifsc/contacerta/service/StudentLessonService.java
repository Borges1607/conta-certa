package com.ifsc.contacerta.service;

import com.ifsc.contacerta.dto.shared.PageResponse;
import com.ifsc.contacerta.dto.studentlesson.AttemptHistoryResponse;
import com.ifsc.contacerta.dto.studentlesson.StudentLessonDetailResponse;
import com.ifsc.contacerta.dto.studentlesson.StudentLessonPathResponse;
import com.ifsc.contacerta.entity.LessonAssignment;
import com.ifsc.contacerta.exception.ApiException;
import com.ifsc.contacerta.model.AttemptAvailabilityStatus;
import com.ifsc.contacerta.model.AttemptStatus;
import com.ifsc.contacerta.model.ContentStatus;
import com.ifsc.contacerta.model.MembershipStatus;
import com.ifsc.contacerta.repository.AttemptRepository;
import com.ifsc.contacerta.repository.ExtraAttemptGrantRepository;
import com.ifsc.contacerta.repository.LessonAssignmentRepository;
import com.ifsc.contacerta.repository.RoomMembershipRepository;
import com.ifsc.contacerta.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StudentLessonService {
	private static final List<AttemptStatus> FINAL = List.of(AttemptStatus.SUBMITTED, AttemptStatus.EXPIRED);
	private final RoomMembershipRepository membershipRepository;
	private final LessonAssignmentRepository assignmentRepository;
	private final AttemptRepository attemptRepository;
	private final ExtraAttemptGrantRepository grantRepository;
	private final UserRepository userRepository;
	private final Clock clock;

	@Transactional(readOnly = true)
	public List<StudentLessonPathResponse> path(UUID studentId, UUID roomId) {
		requireStudent(studentId);
		requireMembership(studentId, roomId);
		Instant now = Instant.now(clock);
		return assignmentRepository.findByRoomIdAndStatusOrderByPositionAsc(roomId, ContentStatus.PUBLISHED).stream()
				.map(assignment -> pathResponse(studentId, assignment, now)).toList();
	}

	@Transactional(readOnly = true)
	public StudentLessonDetailResponse detail(UUID studentId, UUID roomId, UUID lessonId) {
		requireStudent(studentId);
		requireMembership(studentId, roomId);
		LessonAssignment assignment = assignmentRepository.findByRoomIdAndLessonId(roomId, lessonId)
				.filter(candidate -> candidate.getStatus() == ContentStatus.PUBLISHED)
				.orElseThrow(() -> error("ASSIGNMENT_NOT_FOUND", "Assignment was not found."));
		return new StudentLessonDetailResponse(assignment.getId(), lessonId, assignment.getLesson().getTitle(),
				assignment.getLesson().getSummary(), assignment.getLesson().getTheoryMarkdown(), assignment.getPosition(),
				availability(studentId, assignment, Instant.now(clock)), assignment.getAvailableFrom(), assignment.getDueAt(),
				assignment.getTimeLimitMinutes(), assignment.getMaxAttempts(), assignment.getQuestionCount());
	}

	@Transactional(readOnly = true)
	public PageResponse<AttemptHistoryResponse> history(UUID studentId, UUID assignmentId, Pageable pageable) {
		requireStudent(studentId);
		return PageResponse.from(attemptRepository.findByAssignmentIdAndStudentIdOrderBySequenceDesc(assignmentId, studentId, pageable)
				.map(attempt -> new AttemptHistoryResponse(attempt.getId(), attempt.getSequence(), attempt.getStatus(),
						attempt.getScorePercent(), attempt.getPassed(), attempt.getStartedAt(), attempt.getSubmittedAt())));
	}

	private StudentLessonPathResponse pathResponse(UUID studentId, LessonAssignment assignment, Instant now) {
		long used = attemptRepository.countByAssignmentIdAndStudentId(assignment.getId(), studentId);
		long granted = grantRepository.sumQuantityByAssignmentIdAndStudentId(assignment.getId(), studentId);
		boolean resumable = attemptRepository.findByAssignmentIdAndStudentIdAndStatus(assignment.getId(), studentId, AttemptStatus.IN_PROGRESS).isPresent();
		Long available = assignment.getMaxAttempts() == null ? null : Math.max(0, assignment.getMaxAttempts() + granted - used);
		return new StudentLessonPathResponse(assignment.getId(), assignment.getLesson().getId(), assignment.getLesson().getTitle(),
				assignment.getLesson().getSummary(), assignment.getPosition(), availability(studentId, assignment, now), used, available,
				attemptRepository.findBestScoreByAssignmentIdAndStudentIdAndStatusIn(assignment.getId(), studentId, FINAL), resumable, assignment.getDueAt());
	}

	private AttemptAvailabilityStatus availability(UUID studentId, LessonAssignment assignment, Instant now) {
		if (assignment.getAvailableFrom() != null && now.isBefore(assignment.getAvailableFrom())) return AttemptAvailabilityStatus.NOT_OPEN_YET;
		if (assignment.getDueAt() != null && !now.isBefore(assignment.getDueAt())) return AttemptAvailabilityStatus.CLOSED;
		if (assignment.getPosition() > 1 && !hasPassedPrevious(studentId, assignment)) return AttemptAvailabilityStatus.PREREQUISITE_REQUIRED;
		if (assignment.getMaxAttempts() != null && attemptRepository.countByAssignmentIdAndStudentId(assignment.getId(), studentId)
				>= assignment.getMaxAttempts() + grantRepository.sumQuantityByAssignmentIdAndStudentId(assignment.getId(), studentId)) return AttemptAvailabilityStatus.ATTEMPT_LIMIT_REACHED;
		return AttemptAvailabilityStatus.AVAILABLE;
	}

	private boolean hasPassedPrevious(UUID studentId, LessonAssignment assignment) {
		return assignmentRepository.findByRoomIdAndStatusOrderByPositionAsc(assignment.getRoom().getId(), ContentStatus.PUBLISHED).stream()
				.filter(candidate -> candidate.getPosition() < assignment.getPosition()).max(java.util.Comparator.comparingInt(LessonAssignment::getPosition))
				.map(previous -> attemptRepository.findByAssignmentIdAndStudentIdAndStatus(previous.getId(), studentId, AttemptStatus.SUBMITTED)
						.map(attempt -> Boolean.TRUE.equals(attempt.getPassed())).orElse(false)).orElse(true);
	}

	private void requireMembership(UUID studentId, UUID roomId) {
		membershipRepository.findByRoomIdAndStudentId(roomId, studentId).filter(membership -> membership.getStatus() == MembershipStatus.ACTIVE)
				.orElseThrow(() -> error("MEMBERSHIP_NOT_FOUND", "Membership was not found."));
	}

	private void requireStudent(UUID studentId) {
		var student = userRepository.findById(studentId)
				.orElseThrow(() -> error("STUDENT_NOT_FOUND", "Student was not found."));
		if (student.getRole() != com.ifsc.contacerta.model.Role.STUDENT) {
			throw new ApiException(HttpStatus.FORBIDDEN, "STUDENT_REQUIRED", "A student account is required.");
		}
		if (student.getStatus() != com.ifsc.contacerta.model.AccountStatus.ACTIVE) {
			throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_INACTIVE", "Student account is inactive.");
		}
	}

	private ApiException error(String code, String message) { return new ApiException(HttpStatus.NOT_FOUND, code, message); }
}
