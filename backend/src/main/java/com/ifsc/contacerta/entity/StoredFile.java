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
import java.util.Arrays;
import java.util.UUID;

@Entity
@Table(name = "stored_files")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoredFile {

	@Id
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "owner_teacher_id", nullable = false)
	private User ownerTeacher;

	@Column(name = "file_name", nullable = false, length = 255)
	private String fileName;

	@Column(name = "content_type", nullable = false, length = 150)
	private String contentType;

	@Column(name = "size_bytes", nullable = false)
	private long sizeBytes;

	@Column(nullable = false, length = 64)
	private String sha256;

	@Getter(AccessLevel.NONE)
	@Column(nullable = false, columnDefinition = "bytea")
	private byte[] content;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	public StoredFile(
			User ownerTeacher,
			String fileName,
			String contentType,
			long sizeBytes,
			String sha256,
			byte[] content,
			Instant createdAt
	) {
		this.id = UUID.randomUUID();
		this.ownerTeacher = ownerTeacher;
		this.fileName = fileName;
		this.contentType = contentType;
		this.sizeBytes = sizeBytes;
		this.sha256 = sha256;
		this.content = Arrays.copyOf(content, content.length);
		this.createdAt = createdAt;
	}

	public byte[] getContent() {
		return Arrays.copyOf(content, content.length);
	}
}
