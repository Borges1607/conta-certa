package com.ifsc.contacerta.entity;

import com.ifsc.contacerta.model.MailMessageType;
import com.ifsc.contacerta.model.MailOutboxStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "mail_outbox")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MailOutboxMessage {
	@Id private UUID id;
	@Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private MailMessageType type;
	@Column(nullable = false, length = 254) private String recipient;
	@Column(nullable = false, length = 200) private String subject;
	@Column(name = "text_body", nullable = false) private String textBody;
	@Column(name = "html_body", nullable = false) private String htmlBody;
	@Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private MailOutboxStatus status;
	@Column(name = "attempt_count", nullable = false) private int attemptCount;
	@Column(name = "next_attempt_at", nullable = false) private Instant nextAttemptAt;
	@Column(name = "claimed_at") private Instant claimedAt;
	@Column(name = "sent_at") private Instant sentAt;
	@Column(name = "last_error", length = 500) private String lastError;
	@Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
	@Version @Column(nullable = false) private long version;

	public MailOutboxMessage(MailMessageType type, String recipient, String subject, String textBody, String htmlBody, Instant now) {
		this.id = UUID.randomUUID(); this.type = type; this.recipient = recipient; this.subject = subject;
		this.textBody = textBody; this.htmlBody = htmlBody; this.status = MailOutboxStatus.PENDING;
		this.nextAttemptAt = now; this.createdAt = now;
	}
	public void claim(Instant now) { status = MailOutboxStatus.SENDING; claimedAt = now; }
	public void releaseClaim() { status = MailOutboxStatus.PENDING; claimedAt = null; }
	public void markSent(Instant now) { status = MailOutboxStatus.SENT; sentAt = now; claimedAt = null; }
	public void scheduleRetry(Instant next, String error) { status = MailOutboxStatus.PENDING; attemptCount++; nextAttemptAt = next; claimedAt = null; lastError = error; }
	public void markFailed(String error) { status = MailOutboxStatus.FAILED; attemptCount++; claimedAt = null; lastError = error; }
}
