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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "media_views")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MediaView {

	@Id
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "student_id", nullable = false)
	private User student;

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

	@Column(name = "first_viewed_at", nullable = false)
	private Instant firstViewedAt;

	@Column(name = "last_viewed_at", nullable = false)
	private Instant lastViewedAt;

	@Column(name = "view_count", nullable = false)
	private long viewCount;

	private MediaView(User student, Room room, Instant viewedAt) {
		this.id = UUID.randomUUID();
		this.student = student;
		this.room = room;
		this.firstViewedAt = viewedAt;
		this.lastViewedAt = viewedAt;
		this.viewCount = 1;
	}

	public static MediaView video(User student, Room room, Video video, Instant viewedAt) {
		MediaView view = new MediaView(student, room, viewedAt);
		view.mediaType = MediaViewType.VIDEO;
		view.video = video;
		return view;
	}

	public static MediaView material(User student, Room room, Material material, Instant viewedAt) {
		MediaView view = new MediaView(student, room, viewedAt);
		view.mediaType = MediaViewType.MATERIAL;
		view.material = material;
		return view;
	}

	public void recordView(Instant viewedAt) {
		lastViewedAt = viewedAt;
		viewCount++;
	}
}
