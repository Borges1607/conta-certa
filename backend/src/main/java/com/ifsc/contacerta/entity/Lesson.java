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
@Table(name = "lessons")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Lesson {

	@Id
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "teacher_id", nullable = false)
	private User teacher;

	@Column(nullable = false, length = 160)
	private String title;

	@Column(length = 500)
	private String summary;

	@Column(name = "theory_markdown", nullable = false, columnDefinition = "text")
	private String theoryMarkdown;

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

	public Lesson(String title, String summary, String theoryMarkdown, User teacher) {
		this.id = UUID.randomUUID();
		this.title = title;
		this.summary = summary;
		this.theoryMarkdown = theoryMarkdown;
		this.teacher = teacher;
		this.status = ContentStatus.DRAFT;
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
