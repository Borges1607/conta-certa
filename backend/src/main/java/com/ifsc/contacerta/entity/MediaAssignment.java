package com.ifsc.contacerta.entity;

import com.ifsc.contacerta.model.MediaViewType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "media_assignments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MediaAssignment {

	@Id
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "room_id", nullable = false)
	private Room room;

	@Enumerated(EnumType.STRING)
	@Column(name = "media_type", nullable = false, length = 16)
	private MediaViewType mediaType;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "video_id")
	private Video video;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "material_id")
	private Material material;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "lesson_assignment_id")
	private LessonAssignment lessonAssignment;

	@Column(nullable = false)
	private int position;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Version
	@Column(nullable = false)
	private long version;

	private MediaAssignment(Room room, LessonAssignment lessonAssignment, int position, Instant createdAt) {
		this.id = UUID.randomUUID();
		this.room = room;
		this.lessonAssignment = lessonAssignment;
		this.position = position;
		this.createdAt = createdAt;
	}

	public static MediaAssignment video(
			Room room,
			Video video,
			LessonAssignment lessonAssignment,
			int position,
			Instant createdAt
	) {
		MediaAssignment assignment = new MediaAssignment(room, lessonAssignment, position, createdAt);
		assignment.mediaType = MediaViewType.VIDEO;
		assignment.video = video;
		return assignment;
	}

	public static MediaAssignment material(
			Room room,
			Material material,
			LessonAssignment lessonAssignment,
			int position,
			Instant createdAt
	) {
		MediaAssignment assignment = new MediaAssignment(room, lessonAssignment, position, createdAt);
		assignment.mediaType = MediaViewType.MATERIAL;
		assignment.material = material;
		return assignment;
	}

	public void update(LessonAssignment lessonAssignment, int position) {
		this.lessonAssignment = lessonAssignment;
		this.position = position;
	}

	public UUID getMediaId() {
		return mediaType == MediaViewType.VIDEO ? video.getId() : material.getId();
	}
}
