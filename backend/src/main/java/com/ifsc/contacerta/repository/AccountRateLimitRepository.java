package com.ifsc.contacerta.repository;

import com.ifsc.contacerta.entity.AccountRateLimit;
import com.ifsc.contacerta.model.AccountRateLimitOperation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;
import java.util.UUID;

public interface AccountRateLimitRepository extends JpaRepository<AccountRateLimit, UUID> {
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<AccountRateLimit> findByOperationAndSubjectHash(AccountRateLimitOperation operation, String subjectHash);
}
