package com.ifsc.contacerta.repository;

import com.ifsc.contacerta.entity.RefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select t from RefreshToken t where t.tokenHash = :hash")
	Optional<RefreshToken> findForUpdateByTokenHash(@Param("hash") String hash);

	@Modifying
	@Query("""
			update RefreshToken t
			set t.revokedAt = :now, t.version = t.version + 1
			where t.session.id = :sessionId and t.revokedAt is null
			""")
	int revokeAllActiveBySessionId(@Param("sessionId") UUID sessionId, @Param("now") Instant now);

	@Modifying
	@Query("""
			update RefreshToken t
			set t.revokedAt = :now, t.version = t.version + 1
			where t.session.user.id = :userId and t.revokedAt is null
			""")
	int revokeAllActiveByUserId(@Param("userId") UUID userId, @Param("now") Instant now);
}
