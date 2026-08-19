package com.ifsc.contacerta.service;

import com.ifsc.contacerta.config.SecurityProperties;
import com.ifsc.contacerta.dto.auth.AuthResponse;
import com.ifsc.contacerta.dto.auth.LoginRequest;
import com.ifsc.contacerta.dto.auth.RefreshRequest;
import com.ifsc.contacerta.entity.AuthSession;
import com.ifsc.contacerta.entity.RefreshToken;
import com.ifsc.contacerta.entity.User;
import com.ifsc.contacerta.exception.ApiException;
import com.ifsc.contacerta.exception.RefreshTokenReusedException;
import com.ifsc.contacerta.model.AccountStatus;
import com.ifsc.contacerta.repository.AuthSessionRepository;
import com.ifsc.contacerta.repository.RefreshTokenRepository;
import com.ifsc.contacerta.repository.UserRepository;
import com.ifsc.contacerta.security.JwtService;
import com.ifsc.contacerta.security.CurrentUser;
import com.ifsc.contacerta.security.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {

	private final UserRepository userRepository;
	private final AuthSessionRepository sessionRepository;
	private final RefreshTokenRepository tokenRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;
	private final RefreshTokenService refreshTokenService;
	private final UserResponseMapper userResponseMapper;
	private final SecurityProperties properties;
	private final Clock clock;

	@Transactional
	public AuthResponse login(LoginRequest request) {
		User user = findActiveUser(request);
		Instant now = clock.instant();
		Instant sessionExpiresAt = now.plus(properties.session().refreshTokenTtl());
		AuthSession session = sessionRepository.save(new AuthSession(user, sessionExpiresAt, now));
		RefreshTokenService.GeneratedRefreshToken generatedRefreshToken = refreshTokenService.generate();
		tokenRepository.save(new RefreshToken(
				session,
				generatedRefreshToken.hash(),
				sessionExpiresAt,
				now
		));

		return new AuthResponse(
				jwtService.issue(user.getId(), user.getRole(), session.getId()),
				generatedRefreshToken.plainText(),
				"Bearer",
				properties.jwt().accessTokenTtl().getSeconds(),
				properties.session().refreshTokenTtl().getSeconds(),
				userResponseMapper.toResponse(user)
		);
	}

	@Transactional(noRollbackFor = RefreshTokenReusedException.class)
	public AuthResponse refresh(RefreshRequest request) {
		if (request == null || request.refreshToken() == null || request.refreshToken().isBlank()) {
			throw invalidRefreshToken();
		}
		String hash = refreshTokenService.hash(request.refreshToken());
		RefreshToken currentToken = tokenRepository.findForUpdateByTokenHash(hash)
				.orElseThrow(this::invalidRefreshToken);
		AuthSession session = currentToken.getSession();
		Instant now = clock.instant();

		if (currentToken.getRotatedAt() != null) {
			session.revoke(now);
			tokenRepository.revokeAllActiveBySessionId(session.getId(), now);
			throw new RefreshTokenReusedException();
		}
		if (currentToken.getRevokedAt() != null
				|| !currentToken.getExpiresAt().isAfter(now)
				|| session.getRevokedAt() != null
				|| !session.getExpiresAt().isAfter(now)
				|| session.getUser().getStatus() != AccountStatus.ACTIVE) {
			throw invalidRefreshToken();
		}

		RefreshTokenService.GeneratedRefreshToken successorValue = refreshTokenService.generate();
		RefreshToken successor = tokenRepository.save(new RefreshToken(
				session,
				successorValue.hash(),
				session.getExpiresAt(),
				now
		));
		currentToken.rotateTo(successor, now);
		session.touch(now);
		User user = session.getUser();

		return new AuthResponse(
				jwtService.issue(user.getId(), user.getRole(), session.getId()),
				successorValue.plainText(),
				"Bearer",
				properties.jwt().accessTokenTtl().getSeconds(),
				Duration.between(now, session.getExpiresAt()).getSeconds(),
				null
		);
	}

	@Transactional
	public void logout(CurrentUser currentUser) {
		if (currentUser == null) {
			return;
		}
		sessionRepository.findWithUserById(currentUser.sessionId()).ifPresent(session -> {
			if (session.getUser().getId().equals(currentUser.userId()) && session.getRevokedAt() == null) {
				Instant now = clock.instant();
				session.revoke(now);
				tokenRepository.revokeAllActiveBySessionId(session.getId(), now);
			}
		});
	}

	private User findActiveUser(LoginRequest request) {
		if (request == null || request.email() == null || request.password() == null) {
			throw invalidCredentials();
		}
		String email = request.email().trim().toLowerCase(Locale.ROOT);
		User user = userRepository.findByEmailIgnoreCase(email).orElseThrow(this::invalidCredentials);
		if (user.getStatus() != AccountStatus.ACTIVE
				|| user.getPasswordHash() == null
				|| !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
			throw invalidCredentials();
		}
		return user;
	}

	private ApiException invalidCredentials() {
		return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Email or password is invalid.");
	}

	private ApiException invalidRefreshToken() {
		return new ApiException(
				HttpStatus.UNAUTHORIZED,
				"INVALID_REFRESH_TOKEN",
				"Refresh token is invalid or expired."
		);
	}
}
