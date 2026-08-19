package com.ifsc.contacerta.service;

import com.ifsc.contacerta.entity.AuthSession;
import com.ifsc.contacerta.exception.ApiException;
import com.ifsc.contacerta.model.AccountStatus;
import com.ifsc.contacerta.repository.AuthSessionRepository;
import com.ifsc.contacerta.security.AccessTokenClaims;
import com.ifsc.contacerta.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
@RequiredArgsConstructor
public class SessionAuthenticationService {

	private final AuthSessionRepository sessionRepository;
	private final Clock clock;

	@Transactional(readOnly = true)
	public CurrentUser authenticate(AccessTokenClaims claims) {
		AuthSession session = sessionRepository.findWithUserById(claims.sessionId())
				.orElseThrow(this::invalidAccessToken);

		boolean invalidSession = session.getRevokedAt() != null
				|| !session.getExpiresAt().isAfter(clock.instant())
				|| !session.getUser().getId().equals(claims.userId())
				|| session.getUser().getStatus() != AccountStatus.ACTIVE
				|| session.getUser().getRole() != claims.role();
		if (invalidSession) {
			throw invalidAccessToken();
		}

		return new CurrentUser(claims.userId(), claims.role(), claims.sessionId());
	}

	private ApiException invalidAccessToken() {
		return new ApiException(
				HttpStatus.UNAUTHORIZED,
				"INVALID_ACCESS_TOKEN",
				"Access token is invalid or expired."
		);
	}
}
