package com.ifsc.contacerta.repository;

import com.ifsc.contacerta.entity.AuthSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {

	@Query("select s from AuthSession s join fetch s.user where s.id = :id")
	Optional<AuthSession> findWithUserById(@Param("id") UUID id);

	@Modifying
	@Query("""
			update AuthSession s
			set s.revokedAt = :now, s.version = s.version + 1
			where s.user.id = :userId and s.revokedAt is null
			""")
	int revokeAllActiveByUserId(@Param("userId") UUID userId, @Param("now") Instant now);
}
