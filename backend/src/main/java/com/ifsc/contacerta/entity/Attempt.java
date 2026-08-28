package com.ifsc.contacerta.entity;

import com.ifsc.contacerta.model.AttemptStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "attempts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Attempt {

	@Id
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "assignment_id", nullable = false)
	private LessonAssignment assignment;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "student_id", nullable = false)
	private User student;

	@Column(nullable = false)
	private int sequence;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private AttemptStatus status;

	@Column(name = "started_at", nullable = false)
	private Instant startedAt;

	@Column(name = "expires_at")
	private Instant expiresAt;

	@Column(name = "submitted_at")
	private Instant submittedAt;

	@Column(name = "total_questions", nullable = false)
	private int totalQuestions;

	@Column(name = "answered_questions", nullable = false)
	private int answeredQuestions;

	@Column(name = "correct_answers", nullable = false)
	private int correctAnswers;

	@Column(name = "score_percent")
	private Integer scorePercent;

	private Boolean passed;

	private Integer stars;

	@Column(name = "xp_credited")
	private Integer xpCredited;

	@Getter(AccessLevel.NONE)
	@OneToMany(mappedBy = "attempt", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("position ASC")
	private List<AttemptQuestionSnapshot> snapshots = new ArrayList<>();

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	@Column(nullable = false)
	private long version;

	public Attempt(LessonAssignment assignment, User student, int sequence, Instant startedAt, Instant expiresAt) {
		this.id = UUID.randomUUID();
		this.assignment = assignment;
		this.student = student;
		this.sequence = sequence;
		this.status = AttemptStatus.IN_PROGRESS;
		this.startedAt = startedAt;
		this.expiresAt = expiresAt;
	}

	public void addSnapshot(Question question, int position, List<QuestionOption> options) {
		snapshots.add(new AttemptQuestionSnapshot(this, question, position, options));
	}

	public List<AttemptQuestionSnapshot> getSnapshots() {
		return List.copyOf(snapshots);
	}

	public void finalizeAs(
			AttemptStatus status,
			Instant submittedAt,
			int totalQuestions,
			int answeredQuestions,
			int correctAnswers,
			boolean passed,
			int stars,
			int xpCredited
	) {
		this.status = status;
		this.submittedAt = submittedAt;
		this.totalQuestions = totalQuestions;
		this.answeredQuestions = answeredQuestions;
		this.correctAnswers = correctAnswers;
		this.scorePercent = totalQuestions == 0
				? 0
				: BigDecimal.valueOf(correctAnswers)
						.multiply(BigDecimal.valueOf(100))
						.divide(BigDecimal.valueOf(totalQuestions), 0, RoundingMode.HALF_UP)
						.intValueExact();
		this.passed = passed;
		this.stars = stars;
		this.xpCredited = xpCredited;
	}

	@PrePersist
	void onCreate() {
		Instant now = Instant.now();
		createdAt = now;
		updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = Instant.now();
	}
}
