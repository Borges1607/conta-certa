package com.ifsc.contacerta.service;

import com.ifsc.contacerta.entity.Attempt;
import com.ifsc.contacerta.entity.AttemptAnswer;
import com.ifsc.contacerta.entity.RoomMembership;
import com.ifsc.contacerta.entity.RoomStudentProgress;
import com.ifsc.contacerta.model.AttemptStatus;
import com.ifsc.contacerta.model.MembershipStatus;
import com.ifsc.contacerta.repository.AttemptAnswerRepository;
import com.ifsc.contacerta.repository.AttemptRepository;
import com.ifsc.contacerta.repository.RoomMembershipRepository;
import com.ifsc.contacerta.repository.RoomStudentProgressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AttemptFinalizationService {

	private static final List<AttemptStatus> FINAL_STATUSES = List.of(
			AttemptStatus.SUBMITTED,
			AttemptStatus.EXPIRED
	);

	private final AttemptRepository attemptRepository;
	private final AttemptAnswerRepository answerRepository;
	private final RoomMembershipRepository membershipRepository;
	private final RoomStudentProgressRepository progressRepository;
	private final AchievementUnlockService achievementUnlockService;

	public void finalizeAttempt(Attempt attempt, AttemptStatus status, Instant finalizedAt) {
		if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) {
			return;
		}

		RoomMembership membership = membershipRepository.findForUpdateByRoomIdAndStudentId(
				attempt.getAssignment().getRoom().getId(),
				attempt.getStudent().getId()
		).filter(candidate -> candidate.getStatus() == MembershipStatus.ACTIVE)
				.orElseThrow(() -> new IllegalStateException("An active attempt requires an active membership."));

		int totalQuestions = attempt.getSnapshots().size();
		List<AttemptAnswer> answers = answerRepository.findByQuestionSnapshotAttemptId(attempt.getId());
		int correctAnswers = (int) answers.stream().filter(AttemptAnswer::isCorrect).count();
		int score = score(correctAnswers, totalQuestions);
		boolean passed = score >= attempt.getAssignment().getRoom().getPassingScorePercent();
		int stars = stars(score);

		int previousBestXp = attemptRepository.findBestXpByAssignmentIdAndStudentIdAndStatusIn(
				attempt.getAssignment().getId(), attempt.getStudent().getId(), FINAL_STATUSES
		);
		int previousBestStars = attemptRepository.findBestStarsByAssignmentIdAndStudentIdAndStatusIn(
				attempt.getAssignment().getId(), attempt.getStudent().getId(), FINAL_STATUSES
		);
		boolean firstCompletion = attemptRepository.countByAssignmentIdAndStudentIdAndStatusIn(
				attempt.getAssignment().getId(), attempt.getStudent().getId(), FINAL_STATUSES
		) == 0;
		boolean firstPass = passed && attemptRepository.countByAssignmentIdAndStudentIdAndStatusInAndPassedTrue(
				attempt.getAssignment().getId(), attempt.getStudent().getId(), FINAL_STATUSES
		) == 0;
		int potentialXp = correctAnswers * 10;
		int xpDelta = Math.max(0, potentialXp - previousBestXp);
		int starsDelta = Math.max(0, stars - previousBestStars);

		attempt.finalizeAs(
				status,
				finalizedAt,
				totalQuestions,
				answers.size(),
				correctAnswers,
				passed,
				stars,
				xpDelta
		);

		RoomStudentProgress progress = progressRepository.findForUpdateByRoomIdAndStudentId(
				membership.getRoom().getId(),
				membership.getStudent().getId()
		).orElseGet(() -> progressRepository.save(new RoomStudentProgress(membership.getRoom(), membership.getStudent())));
		progress.applyResult(xpDelta, starsDelta, firstCompletion, firstPass, finalizedAt);
		achievementUnlockService.evaluate(progress, score, finalizedAt);
	}

	private int score(int correctAnswers, int totalQuestions) {
		if (totalQuestions == 0) {
			return 0;
		}
		return BigDecimal.valueOf(correctAnswers)
				.multiply(BigDecimal.valueOf(100))
				.divide(BigDecimal.valueOf(totalQuestions), 0, RoundingMode.HALF_UP)
				.intValueExact();
	}

	private int stars(int score) {
		if (score < 50) return 0;
		if (score < 70) return 1;
		if (score < 90) return 2;
		return 3;
	}
}
