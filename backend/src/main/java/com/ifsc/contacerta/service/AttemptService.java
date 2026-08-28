package com.ifsc.contacerta.service;

import com.ifsc.contacerta.config.AttemptProperties;
import com.ifsc.contacerta.dto.attempt.AttemptAnswerReceiptResponse;
import com.ifsc.contacerta.dto.attempt.AttemptAnswerReviewResponse;
import com.ifsc.contacerta.dto.attempt.AttemptAnswerValueResponse;
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
import com.ifsc.contacerta.repository.RoomStudentProgressRepository;
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
	private final IdempotencyResponseCodec idempotencyResponseCodec;
	private final AttemptScoringService scoringService;
	private final AttemptFinalizationService finalizationService;
	private final StudentProgressService progressService;
	private final RoomStudentProgressRepository roomProgressRepository;
	private final java.util.random.RandomGenerator randomGenerator;
	@Transactional
	public AttemptStartResult start(UUID studentId, UUID assignmentId, String key) {
		if (key == null || key.isBlank()) throw error(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key is required.");
		User student = requireStudent(studentId);
		LessonAssignment assignment = assignmentRepository.findById(assignmentId).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "ASSIGNMENT_NOT_FOUND", "Assignment was not found."));
		RoomMembership membership = membershipRepository.findForUpdateByRoomIdAndStudentId(assignment.getRoom().getId(), studentId).filter(m -> m.getStatus() == MembershipStatus.ACTIVE).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "MEMBERSHIP_NOT_FOUND", "Membership was not found."));
		Instant now = Instant.now(clock); IdempotencyRecord record = idempotencyRepository.findByUserIdAndKey(studentId, key).orElse(null);
		if (record != null && record.getExpiresAt().isAfter(now)) { if (!record.getRouteScope().equals(assignmentId.toString()) || !record.getRequestHash().equals(hasher.hashStartScope())) throw error(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED", "Idempotency key was reused."); return new AttemptStartResult(HttpStatus.valueOf(record.getResponseStatus()), record.getResponseLocation() == null ? null : URI.create(record.getResponseLocation()), idempotencyResponseCodec.decode(record.getResponseBody())); }
		if (record != null) { idempotencyRepository.delete(record); idempotencyRepository.flush(); }
		Attempt active = attemptRepository.findByAssignmentIdAndStudentIdAndStatus(assignmentId, studentId, AttemptStatus.IN_PROGRESS).orElse(null); if (active != null) return new AttemptStartResult(HttpStatus.OK, null, publicResponse(active));
		if (assignment.getRoom().getArchivedAt() != null) throw error(HttpStatus.CONFLICT, "ROOM_ARCHIVED", "Room is archived."); if (assignment.getStatus() != ContentStatus.PUBLISHED) throw error(HttpStatus.UNPROCESSABLE_CONTENT, "ASSIGNMENT_NOT_AVAILABLE", "Assignment is not published."); if (assignment.getAvailableFrom() != null && now.isBefore(assignment.getAvailableFrom())) throw error(HttpStatus.UNPROCESSABLE_CONTENT, "ASSIGNMENT_NOT_AVAILABLE", "Assignment is not available."); if (assignment.getDueAt() != null && !now.isBefore(assignment.getDueAt())) throw error(HttpStatus.UNPROCESSABLE_CONTENT, "ASSIGNMENT_CLOSED", "Assignment is closed.");
		LessonAssignment previous = assignmentRepository.findByRoomIdAndStatusOrderByPositionAsc(assignment.getRoom().getId(), ContentStatus.PUBLISHED).stream()
				.filter(candidate -> candidate.getPosition() < assignment.getPosition())
				.max(java.util.Comparator.comparingInt(LessonAssignment::getPosition)).orElse(null);
		if (previous != null && !progressService.hasPassedAssignment(studentId, previous.getId())) throw error(HttpStatus.CONFLICT, "PREREQUISITE_NOT_MET", "The previous assignment must be passed first.");
		long used = attemptRepository.countByAssignmentIdAndStudentId(assignmentId, studentId); long allowed = assignment.getMaxAttempts() == null ? Long.MAX_VALUE : assignment.getMaxAttempts() + grantRepository.sumQuantityByAssignmentIdAndStudentId(assignmentId, studentId); if (used >= allowed) throw error(HttpStatus.CONFLICT, "ATTEMPT_LIMIT_REACHED", "Attempt limit reached.");
		List<Question> questions = questionRepository.findByLessonIdAndActiveTrueOrderByPositionAsc(assignment.getLesson().getId()); int wanted = assignment.getQuestionCount() == null ? questions.size() : assignment.getQuestionCount(); if (questions.size() < wanted || wanted == 0) throw error(HttpStatus.UNPROCESSABLE_CONTENT, "ASSIGNMENT_CONTENT_UNAVAILABLE", "Assignment content is unavailable.");
		Instant expiresAt = assignment.getTimeLimitMinutes() == null ? assignment.getDueAt() : now.plusSeconds(assignment.getTimeLimitMinutes() * 60L); if (assignment.getDueAt() != null && (expiresAt == null || assignment.getDueAt().isBefore(expiresAt))) expiresAt = assignment.getDueAt();
		List<Question> selectedQuestions = new java.util.ArrayList<>(questions);
		if (assignment.isShuffleQuestions()) java.util.Collections.shuffle(selectedQuestions, new java.util.Random(randomGenerator.nextLong()));
		Attempt attempt = new Attempt(assignment, student, (int) used + 1, now, expiresAt);
		for (int index = 0; index < wanted; index++) {
			List<com.ifsc.contacerta.entity.QuestionOption> options = new java.util.ArrayList<>(selectedQuestions.get(index).getOptions());
			if (assignment.isShuffleOptions()) java.util.Collections.shuffle(options, new java.util.Random(randomGenerator.nextLong()));
			attempt.addSnapshot(selectedQuestions.get(index), index + 1, options);
		}
		attemptRepository.saveAndFlush(attempt); AttemptResponse body = publicResponse(attempt); URI location = URI.create("/student/attempts/" + attempt.getId()); idempotencyRepository.save(new IdempotencyRecord(student, "POST", assignmentId.toString(), key, hasher.hashStartScope(), 201, "application/json", location.toString(), idempotencyResponseCodec.encode(body), attempt, now, now.plus(properties.idempotencyTtl()))); return new AttemptStartResult(HttpStatus.CREATED, location, body);
	}
	private ApiException error(HttpStatus status, String code, String message) { return new ApiException(status, code, message); }
	@Transactional
	public AttemptResponse get(UUID studentId, UUID attemptId) {
		Attempt attempt = ownedLockedAttempt(studentId, attemptId);
		finalizeIfExpired(attempt);
		return publicResponse(attempt);
	}
	@Transactional
	public AttemptAnswerReceiptResponse answer(
			UUID studentId,
			UUID attemptId,
			UUID snapshotId,
			RecordAttemptAnswerRequest request
	) {
		Attempt attempt = ownedLockedAttempt(studentId, attemptId);
		finalizeIfExpired(attempt);
		if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) {
			throw error(HttpStatus.CONFLICT, "ATTEMPT_FINISHED", "Attempt is finished.");
		}
		AttemptQuestionSnapshot snapshot = snapshotRepository.findById(snapshotId)
				.filter(candidate -> candidate.getAttempt().getId().equals(attemptId))
				.orElseThrow(() -> error(HttpStatus.NOT_FOUND, "QUESTION_SNAPSHOT_NOT_FOUND", "Question snapshot was not found."));
		AttemptScoringService.ScoredAnswer scored = scoringService.validateAndScore(snapshot, request);
		AttemptAnswer existing = answerRepository.findByQuestionSnapshotId(snapshotId).orElse(null);
		if (existing != null) {
			if (!sameAnswer(existing, scored)) {
				throw error(HttpStatus.CONFLICT, "ANSWER_ALREADY_RECORDED", "Answer was already recorded.");
			}
			return new AttemptAnswerReceiptResponse(snapshotId, existing.getAnsweredAt(), existing.isCorrect());
		}

		Instant now = Instant.now(clock);
		AttemptAnswer saved;
		if (scored.numericValue() != null) {
			saved = AttemptAnswer.numeric(snapshot, scored.numericValue(), scored.correct(), now);
		} else if (scored.booleanValue() != null) {
			saved = AttemptAnswer.booleanAnswer(snapshot, scored.booleanValue(), scored.correct(), now);
		} else {
			saved = AttemptAnswer.choice(snapshot, scored.selectedOptions(), scored.correct(), now);
		}
		answerRepository.save(saved);
		return new AttemptAnswerReceiptResponse(snapshotId, saved.getAnsweredAt(), saved.isCorrect());
	}
	@Transactional
	public AttemptResultResponse submit(UUID studentId, UUID attemptId) {
		Attempt attempt = ownedLockedAttempt(studentId, attemptId);
		if (attempt.getStatus() == AttemptStatus.IN_PROGRESS) {
			Instant now = Instant.now(clock);
			finalizationService.finalizeAttempt(
					attempt,
					isExpired(attempt, now) ? AttemptStatus.EXPIRED : AttemptStatus.SUBMITTED,
					now
			);
		}
		return result(attempt);
	}
	@Transactional
	public AttemptResultResponse result(UUID studentId, UUID attemptId) {
		Attempt attempt = ownedLockedAttempt(studentId, attemptId);
		finalizeIfExpired(attempt);
		return result(attempt);
	}

	private Attempt ownedLockedAttempt(UUID studentId, UUID attemptId) {
		requireStudent(studentId);
		return attemptRepository.findByIdForUpdate(attemptId)
				.filter(attempt -> attempt.getStudent().getId().equals(studentId))
				.orElseThrow(() -> error(HttpStatus.NOT_FOUND, "ATTEMPT_NOT_FOUND", "Attempt was not found."));
	}

	private User requireStudent(UUID studentId) {
		User student = userRepository.findById(studentId)
				.orElseThrow(() -> error(HttpStatus.NOT_FOUND, "STUDENT_NOT_FOUND", "Student was not found."));
		if (student.getRole() != Role.STUDENT) {
			throw error(HttpStatus.FORBIDDEN, "STUDENT_REQUIRED", "A student account is required.");
		}
		if (student.getStatus() != AccountStatus.ACTIVE) {
			throw error(HttpStatus.FORBIDDEN, "ACCOUNT_INACTIVE", "Student account is inactive.");
		}
		return student;
	}

	private void finalizeIfExpired(Attempt attempt) {
		Instant now = Instant.now(clock);
		if (isExpired(attempt, now)) {
			finalizationService.finalizeAttempt(attempt, AttemptStatus.EXPIRED, now);
		}
	}

	private boolean isExpired(Attempt attempt, Instant now) {
		return attempt.getExpiresAt() != null && !now.isBefore(attempt.getExpiresAt());
	}

	private boolean sameAnswer(AttemptAnswer existing, AttemptScoringService.ScoredAnswer incoming) {
		if (incoming.numericValue() != null) {
			return incoming.numericValue().compareTo(existing.getNumericValue()) == 0;
		}
		if (incoming.booleanValue() != null) {
			return incoming.booleanValue().equals(existing.getBooleanValue());
		}
		return existing.getSelectedOptions().stream().map(option -> option.getId()).collect(java.util.stream.Collectors.toSet())
				.equals(incoming.selectedOptions().stream().map(option -> option.getId()).collect(java.util.stream.Collectors.toSet()));
	}

	private AttemptResponse publicResponse(Attempt attempt) {
		return mapper.toPublicResponse(
				attempt,
				answerRepository.findByQuestionSnapshotAttemptId(attempt.getId())
		);
	}

	private AttemptResultResponse result(Attempt attempt) {
		if (attempt.getStatus() == AttemptStatus.IN_PROGRESS) {
			throw error(HttpStatus.CONFLICT, "ATTEMPT_IN_PROGRESS", "Attempt is in progress.");
		}
		LessonAssignment assignment = attempt.getAssignment();
		UUID roomId = assignment.getRoom().getId();
		UUID studentId = attempt.getStudent().getId();
		int roomXpTotal = roomProgressRepository.findByRoomIdAndStudentId(roomId, studentId)
				.map(progress -> progress.getTotalXp())
				.orElse(0);
		long used = attemptRepository.countByAssignmentIdAndStudentId(assignment.getId(), studentId);
		long granted = grantRepository.sumQuantityByAssignmentIdAndStudentId(assignment.getId(), studentId);
		Long attemptsRemaining = assignment.getMaxAttempts() == null
				? null
				: Math.max(0, assignment.getMaxAttempts() + granted - used);
		return new AttemptResultResponse(
				attempt.getId(),
				assignment.getId(),
				roomId,
				assignment.getLesson().getId(),
				assignment.getLesson().getTitle(),
				attempt.getStatus(),
				attempt.getCorrectAnswers(),
				attempt.getTotalQuestions(),
				attempt.getScorePercent(),
				attempt.getPassed(),
				attempt.getStars(),
				attempt.getXpCredited(),
				roomXpTotal,
				attempt.getStartedAt(),
				attempt.getSubmittedAt(),
				assignment.getRoom().getPassingScorePercent(),
				attemptsRemaining,
				review(attempt)
		);
	}

	private List<AttemptAnswerReviewResponse> review(Attempt attempt) {
		java.util.Map<UUID, AttemptAnswer> answers = answerRepository.findByQuestionSnapshotAttemptId(attempt.getId()).stream()
				.collect(java.util.stream.Collectors.toMap(answer -> answer.getQuestionSnapshot().getId(), answer -> answer));
		return attempt.getSnapshots().stream().map(snapshot -> {
			AttemptAnswer answer = answers.get(snapshot.getId());
			return new AttemptAnswerReviewResponse(
					mapper.toQuestion(snapshot),
					answer == null ? null : mapper.toAnswerValue(answer),
					correctAnswer(snapshot),
					answer != null && answer.isCorrect(),
					snapshot.getExplanation()
			);
		}).toList();
	}

	private AttemptAnswerValueResponse correctAnswer(AttemptQuestionSnapshot snapshot) {
		List<UUID> selectedOptionIds = snapshot.getOptions().stream()
				.filter(option -> option.isCorrect())
				.map(option -> option.getId())
				.toList();
		return new AttemptAnswerValueResponse(
				selectedOptionIds.isEmpty() ? null : selectedOptionIds,
				snapshot.getCorrectBoolean(),
				snapshot.getCorrectNumericValue() == null
						? null
						: snapshot.getCorrectNumericValue().toPlainString()
		);
	}
}
