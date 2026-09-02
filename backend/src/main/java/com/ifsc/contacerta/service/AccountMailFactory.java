package com.ifsc.contacerta.service;

import com.ifsc.contacerta.config.AccountLifecycleProperties;
import com.ifsc.contacerta.model.MailMessageType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class AccountMailFactory {
	private final AccountLifecycleProperties properties;
	public AccountMail verification(String recipient, String token) { return build(MailMessageType.EMAIL_VERIFICATION, recipient, "Confirme seu e-mail", "/verificar-email", token); }
	public AccountMail passwordReset(String recipient, String token) { return build(MailMessageType.PASSWORD_RESET, recipient, "Redefina sua senha", "/redefinir-senha", token); }
	public AccountMail teacherInvitation(String recipient, String token) { return build(MailMessageType.TEACHER_INVITATION, recipient, "Convite para o Conta Certa", "/convite-professor", token); }
	private AccountMail build(MailMessageType type, String recipient, String subject, String path, String token) {
		String base = properties.frontendUrl().replaceAll("/+$", "");
		String link = base + path + "?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
		return new AccountMail(type, recipient, subject, "Acesse o link: " + link, "<p>Acesse o link:</p><p><a href=\"" + HtmlUtils.htmlEscape(link) + "\">Continuar</a></p>");
	}
	public record AccountMail(MailMessageType type, String recipient, String subject, String textBody, String htmlBody) {}
}
