package com.ifsc.contacerta.entity;

import com.ifsc.contacerta.model.ContentStatus;
import com.ifsc.contacerta.model.MaterialKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "materials")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Material {

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

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 24)
	private MaterialKind kind;

	@Column(name = "external_url", length = 2048)
	private String externalUrl;

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "file_id", unique = true)
	private StoredFile file;

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

	private Material(User teacher, String title, String description, String category, Instant createdAt) {
		this.id = UUID.randomUUID();
		this.teacher = teacher;
		this.title = title;
		this.description = description;
		this.category = category;
		this.status = ContentStatus.PUBLISHED;
		this.createdAt = createdAt;
		this.updatedAt = createdAt;
	}

	public static Material file(
			User teacher,
			String title,
			String description,
			String category,
			StoredFile file,
			Instant createdAt
	) {
		Material material = new Material(teacher, title, description, category, createdAt);
		material.kind = MaterialKind.FILE;
		material.file = file;
		return material;
	}

	public static Material externalLink(
			User teacher,
			String title,
			String description,
			String category,
			String externalUrl,
			Instant createdAt
	) {
		Material material = new Material(teacher, title, description, category, createdAt);
		material.kind = MaterialKind.EXTERNAL_LINK;
		material.externalUrl = externalUrl;
		return material;
	}

	public void updateFile(String title, String description, String category, StoredFile file) {
		this.title = title;
		this.description = description;
		this.category = category;
		this.kind = MaterialKind.FILE;
		this.externalUrl = null;
		this.file = file;
	}

	public void updateExternalLink(String title, String description, String category, String externalUrl) {
		this.title = title;
		this.description = description;
		this.category = category;
		this.kind = MaterialKind.EXTERNAL_LINK;
		this.externalUrl = externalUrl;
		this.file = null;
	}

	public void archive() {
		status = ContentStatus.ARCHIVED;
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = Instant.now();
	}
}
