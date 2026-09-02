package com.ifsc.contacerta.repository;

import com.ifsc.contacerta.entity.ActionToken;
import com.ifsc.contacerta.model.ActionTokenType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ActionTokenRepository extends JpaRepository<ActionToken, UUID> {
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select token from ActionToken token join fetch token.user where token.tokenHash = :hash and token.type = :type")
	Optional<ActionToken> findForUpdateByTokenHashAndType(@Param("hash") String hash, @Param("type") ActionTokenType type);
	@Modifying
	@Query("update ActionToken token set token.invalidatedAt = :now where token.user.id = :userId and token.type = :type and token.consumedAt is null and token.invalidatedAt is null")
	int invalidateUsableByUserIdAndType(@Param("userId") UUID userId, @Param("type") ActionTokenType type, @Param("now") Instant now);
}
