package com.ifsc.contacerta.service;

import com.ifsc.contacerta.config.AttemptProperties;
import com.ifsc.contacerta.dto.attempt.AttemptResponse;
import com.ifsc.contacerta.dto.attempt.AttemptStartResult;
import com.ifsc.contacerta.entity.*;
import com.ifsc.contacerta.exception.ApiException;
import com.ifsc.contacerta.mapper.AttemptMapper;
import com.ifsc.contacerta.model.AccountStatus;
import com.ifsc.contacerta.model.AttemptStatus;
import com.ifsc.contacerta.model.ContentStatus;
import com.ifsc.contacerta.model.MembershipStatus;
import com.ifsc.contacerta.model.Role;
import com.ifsc.contacerta.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI; import java.time.Clock; import java.time.Instant; import java.util.List; import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AttemptService {
	private final UserRepository userRepository; private final LessonAssignmentRepository assignmentRepository; private final RoomMembershipRepository membershipRepository; private final QuestionRepository questionRepository; private final AttemptRepository attemptRepository; private final ExtraAttemptGrantRepository grantRepository; private final IdempotencyRecordRepository idempotencyRepository; private final AttemptProperties properties; private final Clock clock; private final AttemptMapper mapper; private final IdempotencyHasher hasher;
	@Transactional
	public AttemptStartResult start(UUID studentId, UUID assignmentId, String key) {
		if (key == null || key.isBlank()) throw error(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key is required.");
		User student = userRepository.findById(studentId).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "STUDENT_NOT_FOUND", "Student was not found."));
		if (student.getRole() != Role.STUDENT) throw error(HttpStatus.FORBIDDEN, "STUDENT_REQUIRED", "A student account is required.");
		if (student.getStatus() != AccountStatus.ACTIVE) throw error(HttpStatus.FORBIDDEN, "ACCOUNT_INACTIVE", "Student account is inactive.");
		LessonAssignment assignment = assignmentRepository.findById(assignmentId).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "ASSIGNMENT_NOT_FOUND", "Assignment was not found."));
		RoomMembership membership = membershipRepository.findForUpdateByRoomIdAndStudentId(assignment.getRoom().getId(), studentId).filter(m -> m.getStatus() == MembershipStatus.ACTIVE).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "MEMBERSHIP_NOT_FOUND", "Membership was not found."));
		Instant now = Instant.now(clock); IdempotencyRecord record = idempotencyRepository.findByUserIdAndKey(studentId, key).orElse(null);
		if (record != null && record.getExpiresAt().isAfter(now)) { if (!record.getRouteScope().equals(assignmentId.toString()) || !record.getRequestHash().equals(hasher.hashStartScope())) throw error(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED", "Idempotency key was reused."); Attempt attempt = record.getAttempt(); return new AttemptStartResult(HttpStatus.valueOf(record.getResponseStatus()), record.getResponseLocation() == null ? null : URI.create(record.getResponseLocation()), mapper.toPublicResponse(attempt, now)); }
		if (record != null) { idempotencyRepository.delete(record); idempotencyRepository.flush(); }
		Attempt active = attemptRepository.findByAssignmentIdAndStudentIdAndStatus(assignmentId, studentId, AttemptStatus.IN_PROGRESS).orElse(null); if (active != null) return new AttemptStartResult(HttpStatus.OK, null, mapper.toPublicResponse(active, now));
		if (assignment.getRoom().getArchivedAt() != null) throw error(HttpStatus.CONFLICT, "ROOM_ARCHIVED", "Room is archived."); if (assignment.getStatus() != ContentStatus.PUBLISHED) throw error(HttpStatus.UNPROCESSABLE_CONTENT, "ASSIGNMENT_NOT_AVAILABLE", "Assignment is not published."); if (assignment.getAvailableFrom() != null && now.isBefore(assignment.getAvailableFrom())) throw error(HttpStatus.UNPROCESSABLE_CONTENT, "ASSIGNMENT_NOT_AVAILABLE", "Assignment is not available."); if (assignment.getDueAt() != null && !now.isBefore(assignment.getDueAt())) throw error(HttpStatus.UNPROCESSABLE_CONTENT, "ASSIGNMENT_CLOSED", "Assignment is closed.");
		long used = attemptRepository.countByAssignmentIdAndStudentId(assignmentId, studentId); long allowed = assignment.getMaxAttempts() == null ? Long.MAX_VALUE : assignment.getMaxAttempts() + grantRepository.sumQuantityByAssignmentIdAndStudentId(assignmentId, studentId); if (used >= allowed) throw error(HttpStatus.CONFLICT, "ATTEMPT_LIMIT_REACHED", "Attempt limit reached.");
		List<Question> questions = questionRepository.findByLessonIdAndActiveTrueOrderByPositionAsc(assignment.getLesson().getId()); int wanted = assignment.getQuestionCount() == null ? questions.size() : assignment.getQuestionCount(); if (questions.size() < wanted || wanted == 0) throw error(HttpStatus.UNPROCESSABLE_CONTENT, "ASSIGNMENT_CONTENT_UNAVAILABLE", "Assignment content is unavailable.");
		Instant expiresAt = assignment.getTimeLimitMinutes() == null ? assignment.getDueAt() : now.plusSeconds(assignment.getTimeLimitMinutes() * 60L); if (assignment.getDueAt() != null && (expiresAt == null || assignment.getDueAt().isBefore(expiresAt))) expiresAt = assignment.getDueAt();
		Attempt attempt = new Attempt(assignment, student, (int) used + 1, now, expiresAt); for (int index = 0; index < wanted; index++) attempt.addSnapshot(questions.get(index), index + 1, questions.get(index).getOptions()); attemptRepository.saveAndFlush(attempt); AttemptResponse body = mapper.toPublicResponse(attempt, now); URI location = URI.create("/student/attempts/" + attempt.getId()); idempotencyRepository.save(new IdempotencyRecord(student, "POST", assignmentId.toString(), key, hasher.hashStartScope(), 201, "application/json", location.toString(), "{}", attempt, now, now.plus(properties.idempotencyTtl()))); return new AttemptStartResult(HttpStatus.CREATED, location, body);
	}
	private ApiException error(HttpStatus status, String code, String message) { return new ApiException(status, code, message); }
}
