package com.ifsc.contacerta.storage;

import com.ifsc.contacerta.entity.StoredFile;
import com.ifsc.contacerta.entity.User;
import com.ifsc.contacerta.repository.StoredFileRepository;
import com.ifsc.contacerta.service.MaterialFileValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PostgresFileStorage implements FileStorage {

	private final StoredFileRepository repository;

	@Override
	public StoredFile store(User owner, MaterialFileValidator.ValidatedMaterialFile file, Instant createdAt) {
		byte[] content = file.content();
		return repository.save(new StoredFile(
				owner,
				file.fileName(),
				file.contentType(),
				content.length,
				sha256(content),
				content,
				createdAt
		));
	}

	@Override
	public Optional<StoredFile> findById(UUID fileId) {
		return repository.findById(fileId);
	}

	private String sha256(byte[] content) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable.", exception);
		}
	}
}
