package com.ifsc.contacerta.repository;

import com.ifsc.contacerta.entity.Attempt;
import com.ifsc.contacerta.model.AttemptStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface AttemptRepository extends JpaRepository<Attempt, UUID> {

	Optional<Attempt> findByIdAndStudentId(UUID id, UUID studentId);

	Optional<Attempt> findByAssignmentIdAndStudentIdAndStatus(
			UUID assignmentId,
			UUID studentId,
			AttemptStatus status
	);

	long countByAssignmentIdAndStudentId(UUID assignmentId, UUID studentId);

	long countByAssignmentIdAndStudentIdAndStatusIn(
			UUID assignmentId,
			UUID studentId,
			List<AttemptStatus> statuses
	);

	long countByAssignmentIdAndStudentIdAndStatusAndPassedTrue(
			UUID assignmentId,
			UUID studentId,
			AttemptStatus status
	);

	@Query("select coalesce(max(attempt.xpCredited), 0) from Attempt attempt "
			+ "where attempt.assignment.id = :assignmentId and attempt.student.id = :studentId "
			+ "and attempt.status in :statuses")
	int findBestXpByAssignmentIdAndStudentIdAndStatusIn(
			@Param("assignmentId") UUID assignmentId,
			@Param("studentId") UUID studentId,
			@Param("statuses") List<AttemptStatus> statuses
	);

	@Query("select coalesce(max(attempt.stars), 0) from Attempt attempt "
			+ "where attempt.assignment.id = :assignmentId and attempt.student.id = :studentId "
			+ "and attempt.status in :statuses")
	int findBestStarsByAssignmentIdAndStudentIdAndStatusIn(
			@Param("assignmentId") UUID assignmentId,
			@Param("studentId") UUID studentId,
			@Param("statuses") List<AttemptStatus> statuses
	);

	@Query("select attempt.id from Attempt attempt where attempt.status = :status "
			+ "and attempt.expiresAt is not null and attempt.expiresAt <= :now order by attempt.expiresAt")
	List<UUID> findExpiredIds(
			@Param("status") AttemptStatus status,
			@Param("now") java.time.Instant now,
		org.springframework.data.domain.Pageable pageable
	);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select attempt from Attempt attempt where attempt.id = :id")
	Optional<Attempt> findByIdForUpdate(@Param("id") UUID id);
}
