package com.ifsc.contacerta.service;

import com.ifsc.contacerta.dto.shared.PageResponse;
import com.ifsc.contacerta.dto.studentlesson.AttemptHistoryResponse;
import com.ifsc.contacerta.dto.studentlesson.LessonRulesResponse;
import com.ifsc.contacerta.dto.studentlesson.StudentLessonDetailResponse;
import com.ifsc.contacerta.dto.studentlesson.StudentLessonPathResponse;
import com.ifsc.contacerta.entity.Attempt;
import com.ifsc.contacerta.entity.LessonAssignment;
import com.ifsc.contacerta.exception.ApiException;
import com.ifsc.contacerta.model.AccountStatus;
import com.ifsc.contacerta.model.AttemptAvailabilityStatus;
import com.ifsc.contacerta.model.AttemptStatus;
import com.ifsc.contacerta.model.ContentStatus;
import com.ifsc.contacerta.model.LessonLockReason;
import com.ifsc.contacerta.model.MembershipStatus;
import com.ifsc.contacerta.model.Role;
import com.ifsc.contacerta.repository.AttemptRepository;
import com.ifsc.contacerta.repository.ExtraAttemptGrantRepository;
import com.ifsc.contacerta.repository.LessonAssignmentRepository;
import com.ifsc.contacerta.repository.QuestionRepository;
import com.ifsc.contacerta.repository.RoomMembershipRepository;
import com.ifsc.contacerta.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
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
	private final QuestionRepository questionRepository;
	private final Clock clock;

	@Transactional(readOnly = true)
	public List<StudentLessonPathResponse> path(UUID studentId, UUID roomId) {
		requireStudent(studentId);
		requireMembership(studentId, roomId);
		Instant now = Instant.now(clock);
		return assignmentRepository.findAccessibleByRoomIdAndStudentIdAndStatusOrderByPositionAsc(
				roomId, studentId, MembershipStatus.ACTIVE, ContentStatus.PUBLISHED
		).stream()
				.map(assignment -> pathResponse(studentId, assignment, now)).toList();
	}

	@Transactional(readOnly = true)
	public StudentLessonDetailResponse detail(UUID studentId, UUID roomId, UUID lessonId) {
		requireStudent(studentId);
		requireMembership(studentId, roomId);
		LessonAssignment assignment = assignmentRepository.findAccessibleByRoomIdAndLessonIdAndStudentId(
				roomId, lessonId, studentId, MembershipStatus.ACTIVE
		)
				.filter(candidate -> candidate.getStatus() == ContentStatus.PUBLISHED)
				.orElseThrow(() -> error("ASSIGNMENT_NOT_FOUND", "Assignment was not found."));
		LessonState state = lessonState(studentId, assignment, Instant.now(clock));
		return new StudentLessonDetailResponse(
				assignment.getId(),
				lessonId,
				roomId,
				assignment.getLesson().getTitle(),
				assignment.getLesson().getSummary(),
				assignment.getLesson().getTheoryMarkdown(),
				List.of(),
				state.availability(),
				state.lockReason(),
				assignment.getAvailableFrom(),
				assignment.getDueAt(),
				rules(assignment, state),
				state.best().map(Attempt::getScorePercent).orElse(null),
				state.best().map(Attempt::getStars).orElse(null),
				state.active().map(Attempt::getId).orElse(null),
				state.best().map(Attempt::getId).orElse(null)
		);
	}

	@Transactional(readOnly = true)
	public PageResponse<AttemptHistoryResponse> history(
			UUID studentId,
			UUID roomId,
			UUID lessonId,
			Pageable pageable
	) {
		requireStudent(studentId);
		requireMembership(studentId, roomId);
		LessonAssignment assignment = assignmentRepository.findAccessibleByRoomIdAndLessonIdAndStudentId(
				roomId, lessonId, studentId, MembershipStatus.ACTIVE
		)
				.filter(candidate -> candidate.getStatus() == ContentStatus.PUBLISHED)
				.orElseThrow(() -> error("ASSIGNMENT_NOT_FOUND", "Assignment was not found."));
		UUID bestAttemptId = attemptRepository
				.findFirstByAssignmentIdAndStudentIdAndStatusInOrderByScorePercentDescSubmittedAtAsc(
						assignment.getId(), studentId, FINAL
				)
				.map(Attempt::getId)
				.orElse(null);
		return PageResponse.from(attemptRepository.findByAssignmentIdAndStudentIdOrderBySequenceDesc(assignment.getId(), studentId, pageable)
				.map(attempt -> new AttemptHistoryResponse(
						attempt.getId(),
						attempt.getStatus(),
						attempt.getStartedAt(),
						attempt.getSubmittedAt(),
						attempt.getScorePercent(),
						attempt.getStars(),
						Boolean.TRUE.equals(attempt.getPassed()),
						attempt.getCorrectAnswers(),
						attempt.getTotalQuestions(),
						attempt.getId().equals(bestAttemptId)
				)));
	}

	private StudentLessonPathResponse pathResponse(UUID studentId, LessonAssignment assignment, Instant now) {
		LessonState state = lessonState(studentId, assignment, now);
		return new StudentLessonPathResponse(
				assignment.getId(),
				assignment.getLesson().getId(),
				assignment.getLesson().getTitle(),
				assignment.getLesson().getSummary(),
				assignment.getPosition(),
				state.availability(),
				state.lockReason(),
				assignment.getAvailableFrom(),
				assignment.getDueAt(),
				rules(assignment, state),
				state.best().map(Attempt::getScorePercent).orElse(null),
				state.best().map(Attempt::getStars).orElse(null),
				state.active().map(Attempt::getId).orElse(null),
				state.active().map(Attempt::getExpiresAt).orElse(null),
				state.best().map(Attempt::getId).orElse(null)
		);
	}

	private LessonState lessonState(UUID studentId, LessonAssignment assignment, Instant now) {
		long used = attemptRepository.countByAssignmentIdAndStudentId(assignment.getId(), studentId);
		long granted = grantRepository.sumQuantityByAssignmentIdAndStudentId(assignment.getId(), studentId);
		Long remaining = assignment.getMaxAttempts() == null
				? null
				: Math.max(0, assignment.getMaxAttempts() + granted - used);
		Optional<Attempt> best = attemptRepository
				.findFirstByAssignmentIdAndStudentIdAndStatusInOrderByScorePercentDescSubmittedAtAsc(
						assignment.getId(), studentId, FINAL
				);
		Optional<Attempt> active = attemptRepository.findByAssignmentIdAndStudentIdAndStatus(
				assignment.getId(), studentId, AttemptStatus.IN_PROGRESS
		);

		if (assignment.getAvailableFrom() != null && now.isBefore(assignment.getAvailableFrom())) {
			return new LessonState(AttemptAvailabilityStatus.LOCKED, LessonLockReason.NOT_YET_AVAILABLE, used, remaining, best, active);
		}
		boolean passed = best.map(Attempt::getPassed).map(Boolean.TRUE::equals).orElse(false);
		if (assignment.getPosition() > 1 && !passed && !hasPassedPrevious(studentId, assignment)) {
			return new LessonState(AttemptAvailabilityStatus.LOCKED, LessonLockReason.PREREQUISITE_NOT_PASSED, used, remaining, best, active);
		}
		if (active.isPresent()) {
			return new LessonState(AttemptAvailabilityStatus.IN_PROGRESS, null, used, remaining, best, active);
		}
		if (assignment.getDueAt() != null && !now.isBefore(assignment.getDueAt())) {
			return best.isPresent()
					? completedState(passed, used, remaining, best, active)
					: new LessonState(AttemptAvailabilityStatus.LOCKED, LessonLockReason.DUE_DATE_PASSED, used, remaining, best, active);
		}
		if (remaining != null && remaining == 0) {
			return best.isPresent()
					? completedState(passed, used, remaining, best, active)
					: new LessonState(AttemptAvailabilityStatus.LOCKED, LessonLockReason.NO_ATTEMPTS_LEFT, used, remaining, best, active);
		}
		return best.isPresent()
				? completedState(passed, used, remaining, best, active)
				: new LessonState(AttemptAvailabilityStatus.AVAILABLE, null, used, remaining, best, active);
	}

	private LessonState completedState(
			boolean passed,
			long used,
			Long remaining,
			Optional<Attempt> best,
			Optional<Attempt> active
	) {
		return new LessonState(
				passed ? AttemptAvailabilityStatus.PASSED : AttemptAvailabilityStatus.FAILED,
				null,
				used,
				remaining,
				best,
				active
		);
	}

	private LessonRulesResponse rules(LessonAssignment assignment, LessonState state) {
		long questionCount = assignment.getQuestionCount() == null
				? questionRepository.countByLessonIdAndActiveTrue(assignment.getLesson().getId())
				: assignment.getQuestionCount();
		return new LessonRulesResponse(
				assignment.getTimeLimitMinutes(),
				assignment.getMaxAttempts(),
				state.attemptsUsed(),
				state.attemptsRemaining(),
				questionCount,
				assignment.getRoom().getPassingScorePercent()
		);
	}

	private boolean hasPassedPrevious(UUID studentId, LessonAssignment assignment) {
		return assignmentRepository.findAccessibleByRoomIdAndStudentIdAndStatusOrderByPositionAsc(
				assignment.getRoom().getId(), studentId, MembershipStatus.ACTIVE, ContentStatus.PUBLISHED
		).stream()
				.filter(candidate -> candidate.getPosition() < assignment.getPosition()).max(Comparator.comparingInt(LessonAssignment::getPosition))
				.map(previous -> attemptRepository.findFirstByAssignmentIdAndStudentIdAndStatusInOrderByScorePercentDescSubmittedAtAsc(previous.getId(), studentId, FINAL)
						.map(attempt -> Boolean.TRUE.equals(attempt.getPassed())).orElse(false)).orElse(true);
	}

	private record LessonState(
			AttemptAvailabilityStatus availability,
			LessonLockReason lockReason,
			long attemptsUsed,
			Long attemptsRemaining,
			Optional<Attempt> best,
			Optional<Attempt> active
	) {}

	private void requireMembership(UUID studentId, UUID roomId) {
		membershipRepository.findByRoomIdAndStudentId(roomId, studentId).filter(membership -> membership.getStatus() == MembershipStatus.ACTIVE)
				.orElseThrow(() -> error("MEMBERSHIP_NOT_FOUND", "Membership was not found."));
	}

	private void requireStudent(UUID studentId) {
		var student = userRepository.findById(studentId)
				.orElseThrow(() -> error("STUDENT_NOT_FOUND", "Student was not found."));
		if (student.getRole() != Role.STUDENT) {
			throw new ApiException(HttpStatus.FORBIDDEN, "STUDENT_REQUIRED", "A student account is required.");
		}
		if (student.getStatus() != AccountStatus.ACTIVE) {
			throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_INACTIVE", "Student account is inactive.");
		}
	}

	private ApiException error(String code, String message) { return new ApiException(HttpStatus.NOT_FOUND, code, message); }
}
