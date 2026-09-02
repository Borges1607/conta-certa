package com.ifsc.contacerta.service;

import com.ifsc.contacerta.config.MailOutboxProperties;
import com.ifsc.contacerta.entity.MailOutboxMessage;
import com.ifsc.contacerta.mail.MailMessage;
import com.ifsc.contacerta.mail.MailSender;
import com.ifsc.contacerta.model.MailOutboxStatus;
import com.ifsc.contacerta.repository.MailOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MailOutboxDispatcher {
	private final MailOutboxRepository repository;
	private final MailSender sender;
	private final MailOutboxProperties properties;
	private final Clock clock;
	private final TransactionTemplate transactions;

	@Scheduled(fixedDelayString = "${app.mail.poll-delay-ms:5000}")
	public void dispatch() {
		List<UUID> ids = transactions.execute(status -> claim());
		if (ids == null) return;
		ids.forEach(this::deliver);
	}
	private List<UUID> claim() {
		Instant now = clock.instant();
		repository.releaseExpiredClaims(now.minus(properties.claimLease()), MailOutboxStatus.SENDING, MailOutboxStatus.PENDING);
		List<MailOutboxMessage> messages = repository.findDueForUpdate(now, properties.batchSize());
		messages.forEach(message -> message.claim(now));
		return messages.stream().map(MailOutboxMessage::getId).toList();
	}
	private void deliver(UUID id) {
		MailOutboxMessage message = transactions.execute(status -> repository.findById(id).orElse(null));
		if (message == null) return;
		try {
			sender.send(new MailMessage(message.getRecipient(), message.getSubject(), message.getTextBody(), message.getHtmlBody()));
			transactions.executeWithoutResult(status -> repository.findById(id).ifPresent(item -> item.markSent(clock.instant())));
		} catch (RuntimeException exception) {
			transactions.executeWithoutResult(status -> repository.findById(id).ifPresent(item -> fail(item, exception)));
		}
	}
	private void fail(MailOutboxMessage message, RuntimeException exception) {
		String error = exception.getClass().getSimpleName();
		if (message.getAttemptCount() + 1 >= properties.maxAttempts()) { message.markFailed(error); return; }
		List<Duration> delays = properties.retryDelays();
		Duration delay = delays.get(Math.min(message.getAttemptCount(), delays.size() - 1));
		message.scheduleRetry(clock.instant().plus(delay), error);
	}
}
