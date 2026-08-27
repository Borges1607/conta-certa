package com.ifsc.contacerta.repository;

import com.ifsc.contacerta.entity.ExtraAttemptGrant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

public interface ExtraAttemptGrantRepository extends JpaRepository<ExtraAttemptGrant, UUID> {
	@Query("select coalesce(sum(grant.quantity), 0) from ExtraAttemptGrant grant where grant.assignment.id = :assignmentId and grant.student.id = :studentId")
	long sumQuantityByAssignmentIdAndStudentId(UUID assignmentId, UUID studentId);
}
