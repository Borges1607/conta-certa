package com.ifsc.contacerta.repository;

import com.ifsc.contacerta.entity.AttemptQuestionSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AttemptQuestionSnapshotRepository extends JpaRepository<AttemptQuestionSnapshot, UUID> {
}
