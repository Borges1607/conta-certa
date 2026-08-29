package com.ifsc.contacerta.entity;

import com.ifsc.contacerta.model.AchievementCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "achievement_unlocks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AchievementUnlock {

	@Id
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "room_id", nullable = false)
	private Room room;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "student_id", nullable = false)
	private User student;

	@Enumerated(EnumType.STRING)
	@Column(name = "achievement_code", nullable = false, length = 32)
	private AchievementCode code;

	@Column(name = "unlocked_at", nullable = false)
	private Instant unlockedAt;

	public AchievementUnlock(Room room, User student, AchievementCode code, Instant unlockedAt) {
		this.id = UUID.randomUUID();
		this.room = room;
		this.student = student;
		this.code = code;
		this.unlockedAt = unlockedAt;
	}
}
