package com.ifsc.contacerta.service;

import com.ifsc.contacerta.dto.auth.ChangePasswordRequest;
import com.ifsc.contacerta.dto.auth.UserResponse;
import com.ifsc.contacerta.entity.User;
import com.ifsc.contacerta.exception.ApiException;
import com.ifsc.contacerta.repository.AuthSessionRepository;
import com.ifsc.contacerta.repository.RefreshTokenRepository;
import com.ifsc.contacerta.repository.UserRepository;
import com.ifsc.contacerta.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class CurrentUserService {

	private final UserRepository userRepository;
	private final AuthSessionRepository sessionRepository;
	private final RefreshTokenRepository tokenRepository;
	private final PasswordEncoder passwordEncoder;
	private final PasswordPolicy passwordPolicy;
	private final Clock clock;

	@Transactional(readOnly = true)
	public UserResponse get(CurrentUser currentUser) {
		return toResponse(loadUser(currentUser));
	}

	@Transactional
	public void changePassword(CurrentUser currentUser, ChangePasswordRequest request) {
		User user = loadUser(currentUser);
		if (request == null
				|| request.currentPassword() == null
				|| user.getPasswordHash() == null
				|| !passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
			throw new ApiException(
					HttpStatus.UNPROCESSABLE_CONTENT,
					"CURRENT_PASSWORD_INVALID",
					"Current password is invalid."
			);
		}

		passwordPolicy.validate(request.newPassword());
		if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
			throw new ApiException(
					HttpStatus.UNPROCESSABLE_CONTENT,
					"PASSWORD_REUSE_NOT_ALLOWED",
					"New password must be different from the current password."
			);
		}

		user.changePassword(passwordEncoder.encode(request.newPassword()));
		Instant now = clock.instant();
		sessionRepository.revokeAllActiveByUserId(user.getId(), now);
		tokenRepository.revokeAllActiveByUserId(user.getId(), now);
	}

	private User loadUser(CurrentUser currentUser) {
		if (currentUser == null) {
			throw invalidAccessToken();
		}
		return userRepository.findById(currentUser.userId()).orElseThrow(this::invalidAccessToken);
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

	private ApiException invalidAccessToken() {
		return new ApiException(
				HttpStatus.UNAUTHORIZED,
				"INVALID_ACCESS_TOKEN",
				"Access token is invalid or expired."
		);
	}
}
