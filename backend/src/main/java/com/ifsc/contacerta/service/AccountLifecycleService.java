package com.ifsc.contacerta.service;

import com.ifsc.contacerta.dto.auth.StudentRegistrationRequest;
import com.ifsc.contacerta.entity.Institution;
import com.ifsc.contacerta.entity.User;
import com.ifsc.contacerta.exception.ApiException;
import com.ifsc.contacerta.model.AccountRateLimitOperation;
import com.ifsc.contacerta.model.AccountStatus;
import com.ifsc.contacerta.model.ActionTokenType;
import com.ifsc.contacerta.model.Role;
import com.ifsc.contacerta.repository.AuthSessionRepository;
import com.ifsc.contacerta.repository.InstitutionRepository;
import com.ifsc.contacerta.repository.RefreshTokenRepository;
import com.ifsc.contacerta.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AccountLifecycleService {
	private final UserRepository userRepository;
	private final InstitutionRepository institutionRepository;
	private final AuthSessionRepository sessionRepository;
	private final RefreshTokenRepository refreshTokenRepository;
	private final PasswordEncoder passwordEncoder;
	private final PasswordPolicy passwordPolicy;
	private final ActionTokenService tokenService;
	private final AccountRateLimitService rateLimitService;
	private final AccountMailFactory mailFactory;
	private final MailOutboxService outboxService;
	private final Clock clock;

	@Transactional
	public void registerStudent(StudentRegistrationRequest request) {
		String email = normalize(request.email()); passwordPolicy.validate(request.password());
		if (userRepository.existsByEmailIgnoreCase(email)) throw new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED", "Email is already registered.");
		Institution institution = institutionRepository.findById(request.institutionId())
				.filter(Institution::isActive).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "INSTITUTION_NOT_FOUND", "Institution was not found."));
		User user = new User(Role.STUDENT, AccountStatus.PENDING, request.fullName().trim(), email, request.registrationNumber().trim(), institution);
		user.initializePassword(passwordEncoder.encode(request.password()), false); userRepository.save(user);
		enqueue(mailFactory.verification(email, tokenService.create(user, ActionTokenType.EMAIL_VERIFICATION).plainText()));
	}
	@Transactional
	public void verifyEmail(String token) {
		User user = tokenService.consume(token, ActionTokenType.EMAIL_VERIFICATION);
		if (user.getRole() != Role.STUDENT || user.getStatus() != AccountStatus.PENDING) throw tokenNotFound();
		user.verifyEmail(clock.instant()); user.activate();
	}
	@Transactional
	public void resendVerification(String rawEmail) {
		String email = normalize(rawEmail); rateLimitService.check(email, AccountRateLimitOperation.RESEND_VERIFICATION);
		userRepository.findByEmailIgnoreCase(email).filter(user -> user.getRole() == Role.STUDENT && user.getStatus() == AccountStatus.PENDING && user.getEmailVerifiedAt() == null)
				.ifPresent(user -> enqueue(mailFactory.verification(email, tokenService.create(user, ActionTokenType.EMAIL_VERIFICATION).plainText())));
	}
	@Transactional
	public void forgotPassword(String rawEmail) {
		String email = normalize(rawEmail); rateLimitService.check(email, AccountRateLimitOperation.FORGOT_PASSWORD);
		userRepository.findByEmailIgnoreCase(email).filter(user -> user.getRole() != Role.ADMIN && user.getStatus() == AccountStatus.ACTIVE)
				.ifPresent(user -> enqueue(mailFactory.passwordReset(email, tokenService.create(user, ActionTokenType.PASSWORD_RESET).plainText())));
	}
	@Transactional
	public void resetPassword(String token, String newPassword) {
		passwordPolicy.validate(newPassword); User user = tokenService.consume(token, ActionTokenType.PASSWORD_RESET);
		user.changePassword(passwordEncoder.encode(newPassword)); revokeSessions(user, clock.instant());
	}
	@Transactional
	public ActionTokenService.GeneratedActionToken inviteTeacher(User teacher) {
		if (teacher.getRole() != Role.TEACHER || teacher.getStatus() != AccountStatus.PENDING) throw tokenNotFound();
		var token = tokenService.create(teacher, ActionTokenType.TEACHER_INVITATION);
		enqueue(mailFactory.teacherInvitation(teacher.getEmail(), token.plainText())); return token;
	}
	@Transactional
	public void acceptTeacherInvite(String token, String password) {
		passwordPolicy.validate(password); User teacher = tokenService.consume(token, ActionTokenType.TEACHER_INVITATION);
		if (teacher.getRole() != Role.TEACHER || teacher.getStatus() != AccountStatus.PENDING || teacher.getPasswordHash() != null) throw tokenNotFound();
		teacher.initializePassword(passwordEncoder.encode(password), false); teacher.verifyEmail(clock.instant()); teacher.activate();
	}
	private void enqueue(AccountMailFactory.AccountMail mail) { outboxService.enqueue(mail.type(), mail.recipient(), mail.subject(), mail.textBody(), mail.htmlBody()); }
	private void revokeSessions(User user, Instant now) { sessionRepository.revokeAllActiveByUserId(user.getId(), now); refreshTokenRepository.revokeAllActiveByUserId(user.getId(), now); }
	private String normalize(String email) { return email.trim().toLowerCase(Locale.ROOT); }
	private ApiException tokenNotFound() { return new ApiException(HttpStatus.NOT_FOUND, "ACTION_TOKEN_NOT_FOUND", "Action token was not found."); }
}
