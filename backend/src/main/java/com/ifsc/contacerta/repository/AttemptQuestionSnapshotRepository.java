package com.ifsc.contacerta.repository;

import com.ifsc.contacerta.entity.AttemptQuestionSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AttemptQuestionSnapshotRepository extends JpaRepository<AttemptQuestionSnapshot, UUID> {
	Optional<AttemptQuestionSnapshot> findByIdAndAttemptIdAndAttemptStudentId(
			UUID id,
			UUID attemptId,
			UUID studentId
	);
}
