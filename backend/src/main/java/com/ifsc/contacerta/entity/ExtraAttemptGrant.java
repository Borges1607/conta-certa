package com.ifsc.contacerta.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "extra_attempt_grants")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExtraAttemptGrant {
	@Id private UUID id;
	@ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "assignment_id", nullable = false) private LessonAssignment assignment;
	@ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "student_id", nullable = false) private User student;
	@ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "teacher_id", nullable = false) private User teacher;
	@Column(nullable = false) private int quantity;
	@Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
	public ExtraAttemptGrant(LessonAssignment assignment, User student, User teacher, int quantity, Instant createdAt) {
		this.id = UUID.randomUUID(); this.assignment = assignment; this.student = student; this.teacher = teacher; this.quantity = quantity; this.createdAt = createdAt;
	}
}
