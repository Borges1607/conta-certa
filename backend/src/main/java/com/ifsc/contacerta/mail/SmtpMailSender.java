package com.ifsc.contacerta.mail;

import com.ifsc.contacerta.config.MailOutboxProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SmtpMailSender implements MailSender {
	private final JavaMailSender javaMailSender;
	private final MailOutboxProperties properties;
	@Override public void send(MailMessage message) {
		MimeMessage mimeMessage = javaMailSender.createMimeMessage();
		try {
			MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
			helper.setFrom(properties.from()); helper.setTo(message.recipient()); helper.setSubject(message.subject());
			helper.setText(message.textBody(), message.htmlBody());
		} catch (MessagingException exception) { throw new IllegalStateException("Could not construct email.", exception); }
		javaMailSender.send(mimeMessage);
	}
}
