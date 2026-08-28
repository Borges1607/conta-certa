package com.ifsc.contacerta.service;

import com.ifsc.contacerta.model.AttemptStatus;
import com.ifsc.contacerta.repository.AttemptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StudentProgressService {
	private final AttemptRepository attemptRepository;
	@Transactional(readOnly = true)
	public boolean hasPassedAssignment(UUID studentId, UUID assignmentId) {
		return attemptRepository.existsByAssignmentIdAndStudentIdAndStatusAndPassedTrue(
				assignmentId, studentId, AttemptStatus.SUBMITTED
		);
	}
}
