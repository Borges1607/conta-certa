package com.ifsc.contacerta.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "financial_tips")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FinancialTip {

	@Id
	private UUID id;

	@Column(nullable = false, length = 160)
	private String title;

	@Column(nullable = false, columnDefinition = "text")
	private String content;

	@Column(name = "source_url", length = 2048)
	private String sourceUrl;

	@Column(name = "publication_date", nullable = false)
	private LocalDate publicationDate;

	@Column(nullable = false)
	private boolean active;

	@Column(name = "archived_at")
	private Instant archivedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	@Column(nullable = false)
	private long version;

	public FinancialTip(String title, String content, String sourceUrl, LocalDate publicationDate, boolean active) {
		this.id = UUID.randomUUID();
		this.title = title;
		this.content = content;
		this.sourceUrl = sourceUrl;
		this.publicationDate = publicationDate;
		this.active = active;
	}

	public void update(String title, String content, String sourceUrl, LocalDate publicationDate) {
		ensureNotArchived();
		this.title = title;
		this.content = content;
		this.sourceUrl = sourceUrl;
		this.publicationDate = publicationDate;
	}

	public void activate() {
		ensureNotArchived();
		active = true;
	}

	public void deactivate() {
		ensureNotArchived();
		active = false;
	}

	public void archive(Instant now) {
		if (archivedAt == null) {
			archivedAt = now;
			active = false;
		}
	}

	private void ensureNotArchived() {
		if (archivedAt != null) {
			throw new IllegalStateException("Archived financial tips cannot be changed.");
		}
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
