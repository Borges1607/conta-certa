package com.ifsc.contacerta.repository;

import com.ifsc.contacerta.entity.MailOutboxMessage;
import com.ifsc.contacerta.model.MailOutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface MailOutboxRepository extends JpaRepository<MailOutboxMessage, UUID> {
	@Query(value = "select * from mail_outbox where status = 'PENDING' and next_attempt_at <= :now order by created_at for update skip locked limit :size", nativeQuery = true)
	List<MailOutboxMessage> findDueForUpdate(@Param("now") Instant now, @Param("size") int size);
	@Modifying
	@Query("update MailOutboxMessage message set message.status = :pending, message.claimedAt = null where message.status = :sending and message.claimedAt < :threshold")
	int releaseExpiredClaims(@Param("threshold") Instant threshold, @Param("sending") MailOutboxStatus sending, @Param("pending") MailOutboxStatus pending);
}
