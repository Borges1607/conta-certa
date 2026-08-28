package com.ifsc.contacerta.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "room_student_progress")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoomStudentProgress {
	@Id private UUID id;
	@ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "room_id", nullable = false) private Room room;
	@ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "student_id", nullable = false) private User student;
	@Column(name = "total_xp", nullable = false) private int totalXp;
	@Column(nullable = false) private int level;
	@Column(name = "total_best_stars", nullable = false) private int totalBestStars;
	@Column(name = "completed_assignment_count", nullable = false) private int completedAssignmentCount;
	@Column(name = "passed_assignment_count", nullable = false) private int passedAssignmentCount;
	@Column(name = "last_activity_at") private Instant lastActivityAt;
	@Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
	@Column(name = "updated_at", nullable = false) private Instant updatedAt;
	@Version @Column(nullable = false) private long version;
	public RoomStudentProgress(Room room, User student) { this.id = UUID.randomUUID(); this.room = room; this.student = student; this.level = 1; }
	public void applyResult(int xpDelta, int starsDelta, boolean firstCompletion, boolean firstPass, Instant activityAt) {
		totalXp += xpDelta; totalBestStars += starsDelta; if (firstCompletion) completedAssignmentCount++; if (firstPass) passedAssignmentCount++; level = totalXp / 100 + 1; lastActivityAt = activityAt;
	}
	@PrePersist void onCreate() { Instant now = Instant.now(); createdAt = now; updatedAt = now; }
	@PreUpdate void onUpdate() { updatedAt = Instant.now(); }
}
