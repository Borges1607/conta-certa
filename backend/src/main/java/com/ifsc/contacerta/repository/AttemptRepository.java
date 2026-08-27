package com.ifsc.contacerta.repository;

import com.ifsc.contacerta.entity.Attempt;
import com.ifsc.contacerta.model.AttemptStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AttemptRepository extends JpaRepository<Attempt, UUID> {

	Optional<Attempt> findByIdAndStudentId(UUID id, UUID studentId);

	Optional<Attempt> findByAssignmentIdAndStudentIdAndStatus(
			UUID assignmentId,
			UUID studentId,
			AttemptStatus status
	);

	long countByAssignmentIdAndStudentId(UUID assignmentId, UUID studentId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select attempt from Attempt attempt where attempt.id = :id")
	Optional<Attempt> findByIdForUpdate(@Param("id") UUID id);
}
