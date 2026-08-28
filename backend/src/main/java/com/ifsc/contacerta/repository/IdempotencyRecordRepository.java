package com.ifsc.contacerta.repository;

import com.ifsc.contacerta.entity.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, UUID> {
	Optional<IdempotencyRecord> findByUserIdAndKey(UUID userId, String key);
}
