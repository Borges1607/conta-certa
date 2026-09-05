package com.ifsc.contacerta.storage;

import com.ifsc.contacerta.entity.StoredFile;
import com.ifsc.contacerta.entity.User;
import com.ifsc.contacerta.service.MaterialFileValidator;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface FileStorage {
	StoredFile store(User owner, MaterialFileValidator.ValidatedMaterialFile file, Instant createdAt);
	Optional<StoredFile> findByIdAndOwnerTeacherId(UUID fileId, UUID teacherId);
	Optional<StoredFile> findDownloadableByTeacherId(UUID fileId, UUID teacherId);
	Optional<StoredFile> findDownloadableByStudentId(UUID fileId, UUID studentId);
}
