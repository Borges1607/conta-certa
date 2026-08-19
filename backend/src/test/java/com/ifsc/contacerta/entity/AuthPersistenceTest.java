package com.ifsc.contacerta.entity;

import com.ifsc.contacerta.model.AccountStatus;
import com.ifsc.contacerta.model.Role;
import com.ifsc.contacerta.repository.AuthSessionRepository;
import com.ifsc.contacerta.repository.RefreshTokenRepository;
import com.ifsc.contacerta.repository.UserRepository;
import com.ifsc.contacerta.support.PostgresIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class AuthPersistenceTest extends PostgresIntegrationTest {

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private AuthSessionRepository sessionRepository;

	@Autowired
	private RefreshTokenRepository tokenRepository;

	@Autowired
	private EntityManager entityManager;

	@Test
	void devePersistirSessaoECadeiaDeRefresh() {
		Instant now = Instant.parse("2026-08-19T18:00:00Z");
		User user = userRepository.save(activeAdmin("admin@example.com"));
		AuthSession session = sessionRepository.save(
				new AuthSession(user, now.plus(7, ChronoUnit.DAYS), now)
		);
		RefreshToken first = tokenRepository.save(
				new RefreshToken(session, "a".repeat(64), session.getExpiresAt(), now)
		);
		RefreshToken next = tokenRepository.save(
				new RefreshToken(session, "b".repeat(64), session.getExpiresAt(), now.plusSeconds(10))
		);

		first.rotateTo(next, now.plusSeconds(10));
		entityManager.flush();
		entityManager.clear();

		assertThat(tokenRepository.findForUpdateByTokenHash("a".repeat(64)))
				.isPresent()
				.get()
				.satisfies(token -> {
					assertThat(token.getRotatedAt()).isEqualTo(now.plusSeconds(10));
					assertThat(token.getReplacedBy().getId()).isEqualTo(next.getId());
					assertThat(token.getSession().getUser().getId()).isEqualTo(user.getId());
				});
	}

	@Test
	void deveRevogarSessoesETokensAtivosDoUsuario() {
		Instant now = Instant.parse("2026-08-19T18:00:00Z");
		Instant revokedAt = now.plusSeconds(30);
		User user = userRepository.save(activeAdmin("security@example.com"));
		AuthSession session = sessionRepository.save(
				new AuthSession(user, now.plus(7, ChronoUnit.DAYS), now)
		);
		RefreshToken token = tokenRepository.save(
				new RefreshToken(session, "c".repeat(64), session.getExpiresAt(), now)
		);
		entityManager.flush();
		entityManager.clear();

		assertThat(tokenRepository.revokeAllActiveByUserId(user.getId(), revokedAt)).isOne();
		assertThat(sessionRepository.revokeAllActiveByUserId(user.getId(), revokedAt)).isOne();
		entityManager.flush();
		entityManager.clear();

		assertThat(sessionRepository.findById(session.getId())).get()
				.extracting(AuthSession::getRevokedAt)
				.isEqualTo(revokedAt);
		assertThat(tokenRepository.findById(token.getId())).get()
				.extracting(RefreshToken::getRevokedAt)
				.isEqualTo(revokedAt);
	}

	private User activeAdmin(String email) {
		return new User(Role.ADMIN, AccountStatus.ACTIVE, "Admin", email, null, null);
	}
}
