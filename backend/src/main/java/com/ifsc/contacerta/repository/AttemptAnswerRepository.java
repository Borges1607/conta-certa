package com.ifsc.contacerta.repository;

import com.ifsc.contacerta.entity.AttemptAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface AttemptAnswerRepository extends JpaRepository<AttemptAnswer, UUID> {

	Optional<AttemptAnswer> findByQuestionSnapshotId(UUID questionSnapshotId);
	List<AttemptAnswer> findByQuestionSnapshotAttemptId(UUID attemptId);
}
