package com.ifsc.contacerta.service;

import com.ifsc.contacerta.dto.material.MaterialFileResponse;
import com.ifsc.contacerta.entity.StoredFile;
import com.ifsc.contacerta.entity.User;
import com.ifsc.contacerta.exception.ApiException;
import com.ifsc.contacerta.model.AccountStatus;
import com.ifsc.contacerta.model.Role;
import com.ifsc.contacerta.repository.UserRepository;
import com.ifsc.contacerta.storage.FileStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MaterialFileService {

	private final UserRepository userRepository;
	private final MaterialFileValidator validator;
	private final FileStorage storage;
	private final Clock clock;

	@Transactional
	public MaterialFileResponse upload(UUID teacherId, MultipartFile file) {
		User teacher = requireActiveTeacher(teacherId);
		StoredFile stored = storage.store(teacher, validator.validate(file), Instant.now(clock));
		return new MaterialFileResponse(
				stored.getId(), stored.getFileName(), stored.getContentType(), stored.getSizeBytes()
		);
	}

	private User requireActiveTeacher(UUID teacherId) {
		User teacher = userRepository.findById(teacherId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TEACHER_NOT_FOUND", "Teacher was not found."));
		if (teacher.getRole() != Role.TEACHER) {
			throw new ApiException(HttpStatus.FORBIDDEN, "TEACHER_REQUIRED", "A teacher account is required.");
		}
		if (teacher.getStatus() != AccountStatus.ACTIVE) {
			throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_INACTIVE", "Teacher account is inactive.");
		}
		return teacher;
	}
}
