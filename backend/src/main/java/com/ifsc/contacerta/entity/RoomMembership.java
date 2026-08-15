package com.ifsc.contacerta.entity;

import com.ifsc.contacerta.model.MembershipStatus;
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
@Table(name = "room_memberships")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoomMembership {

	@Id
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "room_id", nullable = false)
	private Room room;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "student_id", nullable = false)
	private User student;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private MembershipStatus status;

	@Column(name = "joined_at", nullable = false)
	private Instant joinedAt;

	@Column(name = "removed_at")
	private Instant removedAt;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "removed_by")
	private User removedBy;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	@Column(nullable = false)
	private long version;

	public RoomMembership(Room room, User student) {
		this.id = UUID.randomUUID();
		this.room = room;
		this.student = student;
		this.status = MembershipStatus.ACTIVE;
	}

	@PrePersist
	void onCreate() {
		Instant now = Instant.now();
		joinedAt = now;
		createdAt = now;
		updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = Instant.now();
	}

	public void remove(User teacher) {
		if (status == MembershipStatus.ACTIVE) {
			status = MembershipStatus.REMOVED;
			removedAt = Instant.now();
			removedBy = teacher;
		}
	}

	public void reactivate() {
		status = MembershipStatus.ACTIVE;
		removedAt = null;
		removedBy = null;
	}

}
