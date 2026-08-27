package com.ifsc.contacerta.entity;

import com.ifsc.contacerta.model.ContentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "lesson_assignments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LessonAssignment {

	@Id
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "room_id", nullable = false)
	private Room room;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "lesson_id", nullable = false)
	private Lesson lesson;

	@Column(nullable = false)
	private int position;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private ContentStatus status;

	@Column(name = "available_from")
	private Instant availableFrom;

	@Column(name = "due_at")
	private Instant dueAt;

	@Column(name = "time_limit_minutes")
	private Integer timeLimitMinutes;

	@Column(name = "max_attempts")
	private Integer maxAttempts;

	@Column(name = "question_count")
	private Integer questionCount;

	@Column(name = "shuffle_questions", nullable = false)
	private boolean shuffleQuestions;

	@Column(name = "shuffle_options", nullable = false)
	private boolean shuffleOptions;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	@Column(nullable = false)
	private long version;

	public LessonAssignment(
			Room room,
			Lesson lesson,
			int position,
			Instant availableFrom,
			Instant dueAt,
			Integer timeLimitMinutes,
			Integer maxAttempts,
			Integer questionCount,
			boolean shuffleQuestions,
			boolean shuffleOptions
	) {
		this.id = UUID.randomUUID();
		this.room = room;
		this.lesson = lesson;
		this.position = position;
		this.status = ContentStatus.DRAFT;
		this.availableFrom = availableFrom;
		this.dueAt = dueAt;
		this.timeLimitMinutes = timeLimitMinutes;
		this.maxAttempts = maxAttempts;
		this.questionCount = questionCount;
		this.shuffleQuestions = shuffleQuestions;
		this.shuffleOptions = shuffleOptions;
	}

	public void configure(
			ContentStatus status,
			Instant availableFrom,
			Instant dueAt,
			Integer timeLimitMinutes,
			Integer maxAttempts,
			Integer questionCount,
			boolean shuffleQuestions,
			boolean shuffleOptions
	) {
		this.status = status;
		this.availableFrom = availableFrom;
		this.dueAt = dueAt;
		this.timeLimitMinutes = timeLimitMinutes;
		this.maxAttempts = maxAttempts;
		this.questionCount = questionCount;
		this.shuffleQuestions = shuffleQuestions;
		this.shuffleOptions = shuffleOptions;
	}

	public void moveTo(int position) {
		this.position = position;
	}

	public void publish() {
		this.status = ContentStatus.PUBLISHED;
	}

	public void archive() {
		this.status = ContentStatus.ARCHIVED;
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
