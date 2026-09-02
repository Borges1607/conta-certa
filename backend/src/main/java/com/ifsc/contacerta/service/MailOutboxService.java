package com.ifsc.contacerta.service;

import com.ifsc.contacerta.entity.MailOutboxMessage;
import com.ifsc.contacerta.model.MailMessageType;
import com.ifsc.contacerta.repository.MailOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;

@Service
@RequiredArgsConstructor
public class MailOutboxService {
	private final MailOutboxRepository repository;
	private final Clock clock;
	public void enqueue(MailMessageType type, String recipient, String subject, String textBody, String htmlBody) {
		repository.save(new MailOutboxMessage(type, recipient, subject, textBody, htmlBody, clock.instant()));
	}
}
