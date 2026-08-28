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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "videos")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Video {

	@Id
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "teacher_id", nullable = false)
	private User teacher;

	@Column(nullable = false, length = 160)
	private String title;

	@Column(length = 1000)
	private String description;

	@Column(length = 120)
	private String category;

	@Column(nullable = false, length = 2048)
	private String url;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private ContentStatus status;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	@Column(nullable = false)
	private long version;

	public Video(User teacher, String title, String description, String category, String url, Instant createdAt) {
		this.id = UUID.randomUUID();
		this.teacher = teacher;
		this.title = title;
		this.description = description;
		this.category = category;
		this.url = url;
		this.status = ContentStatus.PUBLISHED;
		this.createdAt = createdAt;
		this.updatedAt = createdAt;
	}

	public void update(String title, String description, String category, String url) {
		this.title = title;
		this.description = description;
		this.category = category;
		this.url = url;
	}

	public void archive() {
		status = ContentStatus.ARCHIVED;
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = Instant.now();
	}
}
