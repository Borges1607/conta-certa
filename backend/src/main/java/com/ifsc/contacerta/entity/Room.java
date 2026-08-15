package com.ifsc.contacerta.entity;

import com.ifsc.contacerta.model.Grade;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "rooms")
public class Room {

	@Id
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "teacher_id", nullable = false)
	private User teacher;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "institution_id", nullable = false)
	private Institution institution;

	@Column(nullable = false, length = 160)
	private String name;

	@Column(length = 1000)
	private String description;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 24)
	private Grade grade;

	@ElementCollection
	@CollectionTable(name = "room_topics", joinColumns = @JoinColumn(name = "room_id"))
	@OrderColumn(name = "position")
	@Column(name = "topic", nullable = false, length = 120)
	private List<String> contentTopics = new ArrayList<>();

	@Column(name = "passing_score_percent", nullable = false)
	private int passingScorePercent;

	@Column(name = "join_code", nullable = false, unique = true, length = 6)
	private String joinCode;

	@Column(name = "archived_at")
	private Instant archivedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	@Column(nullable = false)
	private long version;

	protected Room() {
	}

	public Room(
			String name,
			String description,
			Grade grade,
			List<String> contentTopics,
			int passingScorePercent,
			String joinCode,
			User teacher,
			Institution institution
	) {
		this.id = UUID.randomUUID();
		this.name = name;
		this.description = description;
		this.grade = grade;
		this.contentTopics = new ArrayList<>(contentTopics);
		this.passingScorePercent = passingScorePercent;
		this.joinCode = joinCode;
		this.teacher = teacher;
		this.institution = institution;
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

	public UUID getId() { return id; }
	public User getTeacher() { return teacher; }
	public Institution getInstitution() { return institution; }
	public String getName() { return name; }
	public String getDescription() { return description; }
	public Grade getGrade() { return grade; }
	public List<String> getContentTopics() { return List.copyOf(contentTopics); }
	public int getPassingScorePercent() { return passingScorePercent; }
	public String getJoinCode() { return joinCode; }
	public Instant getArchivedAt() { return archivedAt; }
	public Instant getCreatedAt() { return createdAt; }
	public Instant getUpdatedAt() { return updatedAt; }
	public long getVersion() { return version; }
}
