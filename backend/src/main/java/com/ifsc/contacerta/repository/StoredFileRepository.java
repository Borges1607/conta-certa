package com.ifsc.contacerta.repository;

import com.ifsc.contacerta.entity.StoredFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface StoredFileRepository extends JpaRepository<StoredFile, UUID> {
	Optional<StoredFile> findByIdAndOwnerTeacherId(UUID id, UUID ownerTeacherId);
}
