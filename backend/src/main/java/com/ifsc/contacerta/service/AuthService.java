package com.ifsc.contacerta.service;

import com.ifsc.contacerta.config.SecurityProperties;
import com.ifsc.contacerta.dto.auth.AuthResponse;
import com.ifsc.contacerta.dto.auth.LoginRequest;
import com.ifsc.contacerta.dto.auth.UserResponse;
import com.ifsc.contacerta.entity.AuthSession;
import com.ifsc.contacerta.entity.RefreshToken;
import com.ifsc.contacerta.entity.User;
import com.ifsc.contacerta.exception.ApiException;
import com.ifsc.contacerta.model.AccountStatus;
import com.ifsc.contacerta.repository.AuthSessionRepository;
import com.ifsc.contacerta.repository.RefreshTokenRepository;
import com.ifsc.contacerta.repository.UserRepository;
import com.ifsc.contacerta.security.JwtService;
import com.ifsc.contacerta.security.RefreshTokenService;
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
public class AuthService {

	private final UserRepository userRepository;
	private final AuthSessionRepository sessionRepository;
	private final RefreshTokenRepository tokenRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;
	private final RefreshTokenService refreshTokenService;
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
				properties.jwt().accessTokenTtl().getSeconds(),
				properties.session().refreshTokenTtl().getSeconds(),
				toResponse(user)
		);
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

	private UserResponse toResponse(User user) {
		return new UserResponse(
				user.getId(),
				user.getFullName(),
				user.getEmail(),
				user.getRole(),
				user.getStatus(),
				user.getInstitution() == null ? null : user.getInstitution().getId(),
				user.isMustChangePassword()
		);
	}

	private ApiException invalidCredentials() {
		return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Email or password is invalid.");
	}
}
