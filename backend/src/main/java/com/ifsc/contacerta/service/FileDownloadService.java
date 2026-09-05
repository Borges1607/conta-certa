package com.ifsc.contacerta.service;

import com.ifsc.contacerta.entity.StoredFile;
import com.ifsc.contacerta.entity.User;
import com.ifsc.contacerta.exception.ApiException;
import com.ifsc.contacerta.mapper.FileDownloadMapper;
import com.ifsc.contacerta.model.AccountStatus;
import com.ifsc.contacerta.model.FileDownload;
import com.ifsc.contacerta.model.Role;
import com.ifsc.contacerta.repository.UserRepository;
import com.ifsc.contacerta.storage.FileStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FileDownloadService {

	private final UserRepository userRepository;
	private final FileStorage storage;
	private final FileDownloadMapper mapper;

	@Transactional(readOnly = true)
	public FileDownload get(UUID userId, UUID fileId) {
		User user = userRepository.findById(userId).orElseThrow(() -> new ApiException(
				HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User was not found."
		));
		if (user.getStatus() != AccountStatus.ACTIVE) {
			throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_INACTIVE", "User account is inactive.");
		}
		if (user.getRole() != Role.TEACHER && user.getRole() != Role.STUDENT) {
			throw new ApiException(HttpStatus.FORBIDDEN, "FILE_ACCESS_FORBIDDEN", "This account cannot download files.");
		}
		Optional<StoredFile> file = user.getRole() == Role.TEACHER
				? storage.findDownloadableByTeacherId(fileId, userId)
				: storage.findDownloadableByStudentId(fileId, userId);
		return mapper.toDownload(file.orElseThrow(() -> new ApiException(
				HttpStatus.NOT_FOUND, "FILE_NOT_FOUND", "File was not found."
		)));
	}
}
