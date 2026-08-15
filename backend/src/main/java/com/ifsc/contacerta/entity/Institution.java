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
import java.util.UUID;

@Entity
@Table(name = "institutions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Institution {

	@Id
	private UUID id;

	@Column(nullable = false, length = 160)
	private String name;

	@Column(nullable = false, unique = true, length = 14)
	private String cnpj;

	@Column(name = "contact_email", nullable = false, length = 254)
	private String contactEmail;

	@Column(name = "contact_phone", nullable = false, length = 24)
	private String contactPhone;

	@Column(nullable = false)
	private boolean active;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	@Column(nullable = false)
	private long version;

	public Institution(
			String name,
			String cnpj,
			String contactEmail,
			String contactPhone,
			boolean active
	) {
		this.id = UUID.randomUUID();
		this.name = name;
		this.cnpj = cnpj;
		this.contactEmail = contactEmail;
		this.contactPhone = contactPhone;
		this.active = active;
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
