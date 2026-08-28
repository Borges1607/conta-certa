package com.ifsc.contacerta.service;

import com.ifsc.contacerta.config.AttemptProperties;
import com.ifsc.contacerta.dto.attempt.AttemptAnswerReceiptResponse;
import com.ifsc.contacerta.dto.attempt.AttemptResponse;
import com.ifsc.contacerta.dto.attempt.AttemptResultResponse;
import com.ifsc.contacerta.dto.attempt.AttemptStartResult;
import com.ifsc.contacerta.dto.attempt.RecordAttemptAnswerRequest;
import com.ifsc.contacerta.entity.Attempt;
import com.ifsc.contacerta.entity.AttemptAnswer;
import com.ifsc.contacerta.entity.AttemptQuestionSnapshot;
import com.ifsc.contacerta.entity.IdempotencyRecord;
import com.ifsc.contacerta.entity.LessonAssignment;
import com.ifsc.contacerta.entity.Question;
import com.ifsc.contacerta.entity.RoomMembership;
import com.ifsc.contacerta.entity.User;
import com.ifsc.contacerta.exception.ApiException;
import com.ifsc.contacerta.mapper.AttemptMapper;
import com.ifsc.contacerta.model.AccountStatus;
import com.ifsc.contacerta.model.AttemptStatus;
import com.ifsc.contacerta.model.ContentStatus;
import com.ifsc.contacerta.model.MembershipStatus;
import com.ifsc.contacerta.model.Role;
import com.ifsc.contacerta.repository.AttemptAnswerRepository;
import com.ifsc.contacerta.repository.AttemptQuestionSnapshotRepository;
import com.ifsc.contacerta.repository.AttemptRepository;
import com.ifsc.contacerta.repository.ExtraAttemptGrantRepository;
import com.ifsc.contacerta.repository.IdempotencyRecordRepository;
import com.ifsc.contacerta.repository.LessonAssignmentRepository;
import com.ifsc.contacerta.repository.QuestionRepository;
import com.ifsc.contacerta.repository.RoomMembershipRepository;
import com.ifsc.contacerta.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AttemptService {
	private final UserRepository userRepository;
	private final LessonAssignmentRepository assignmentRepository;
	private final RoomMembershipRepository membershipRepository;
	private final QuestionRepository questionRepository;
	private final AttemptRepository attemptRepository;
	private final AttemptQuestionSnapshotRepository snapshotRepository;
	private final AttemptAnswerRepository answerRepository;
	private final ExtraAttemptGrantRepository grantRepository;
	private final IdempotencyRecordRepository idempotencyRepository;
	private final AttemptProperties properties;
	private final Clock clock;
	private final AttemptMapper mapper;
	private final IdempotencyHasher hasher;
	private final AttemptScoringService scoringService;
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
	@Transactional(readOnly = true)
	public AttemptResponse get(UUID studentId, UUID attemptId) { Attempt attempt = attemptRepository.findByIdAndStudentId(attemptId, studentId).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "ATTEMPT_NOT_FOUND", "Attempt was not found.")); return mapper.toPublicResponse(attempt, Instant.now(clock)); }
	@Transactional
	public AttemptAnswerReceiptResponse answer(UUID studentId, UUID attemptId, UUID snapshotId, RecordAttemptAnswerRequest request) { Attempt attempt = attemptRepository.findByIdForUpdate(attemptId).filter(a -> a.getStudent().getId().equals(studentId)).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "ATTEMPT_NOT_FOUND", "Attempt was not found.")); if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) throw error(HttpStatus.CONFLICT, "ATTEMPT_FINISHED", "Attempt is finished."); AttemptQuestionSnapshot snapshot = snapshotRepository.findById(snapshotId).filter(s -> s.getAttempt().getId().equals(attemptId)).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "QUESTION_SNAPSHOT_NOT_FOUND", "Question snapshot was not found.")); AttemptAnswer existing = answerRepository.findByQuestionSnapshotId(snapshotId).orElse(null); if (existing != null) return new AttemptAnswerReceiptResponse(existing.isCorrect(), existing.getAnsweredAt()); AttemptScoringService.ScoredAnswer scored = scoringService.validateAndScore(snapshot, request); AttemptAnswer saved; if (scored.numericValue() != null) saved = AttemptAnswer.numeric(snapshot, scored.numericValue(), scored.correct(), Instant.now(clock)); else if (scored.booleanValue() != null) saved = AttemptAnswer.booleanAnswer(snapshot, scored.booleanValue(), scored.correct(), Instant.now(clock)); else saved = AttemptAnswer.choice(snapshot, scored.selectedOptions(), scored.correct(), Instant.now(clock)); answerRepository.save(saved); return new AttemptAnswerReceiptResponse(saved.isCorrect(), saved.getAnsweredAt()); }
	@Transactional
	public AttemptResultResponse submit(UUID studentId, UUID attemptId) { Attempt attempt = attemptRepository.findByIdForUpdate(attemptId).filter(a -> a.getStudent().getId().equals(studentId)).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "ATTEMPT_NOT_FOUND", "Attempt was not found.")); if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) return result(attempt); List<AttemptAnswer> answers = answerRepository.findByQuestionSnapshotAttemptId(attemptId); int total = attempt.getSnapshots().size(); int correct = (int) answers.stream().filter(AttemptAnswer::isCorrect).count(); int score = total == 0 ? 0 : (int) Math.round(correct * 100.0 / total); boolean passed = score >= attempt.getAssignment().getRoom().getPassingScorePercent(); int stars = score < 50 ? 0 : score < 70 ? 1 : score < 90 ? 2 : 3; attempt.finalizeAs(AttemptStatus.SUBMITTED, Instant.now(clock), total, answers.size(), correct, passed, stars, correct * 10); return result(attempt); }
	@Transactional(readOnly = true)
	public AttemptResultResponse result(UUID studentId, UUID attemptId) { Attempt attempt = attemptRepository.findByIdAndStudentId(attemptId, studentId).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "ATTEMPT_NOT_FOUND", "Attempt was not found.")); return result(attempt); }
	private AttemptResultResponse result(Attempt attempt) { if (attempt.getStatus() == AttemptStatus.IN_PROGRESS) throw error(HttpStatus.CONFLICT, "ATTEMPT_IN_PROGRESS", "Attempt is in progress."); return new AttemptResultResponse(attempt.getId(), attempt.getStatus(), attempt.getTotalQuestions(), attempt.getAnsweredQuestions(), attempt.getCorrectAnswers(), attempt.getScorePercent(), attempt.getPassed(), attempt.getStars(), attempt.getXpCredited(), attempt.getSubmittedAt()); }
}
