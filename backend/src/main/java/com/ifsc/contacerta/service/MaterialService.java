package com.ifsc.contacerta.service;

import com.ifsc.contacerta.dto.material.CreateMaterialRequest;
import com.ifsc.contacerta.dto.material.MaterialFileResponse;
import com.ifsc.contacerta.dto.material.PatchMaterialRequest;
import com.ifsc.contacerta.dto.material.TeacherMaterialResponse;
import com.ifsc.contacerta.dto.shared.PageResponse;
import com.ifsc.contacerta.entity.Material;
import com.ifsc.contacerta.entity.StoredFile;
import com.ifsc.contacerta.entity.User;
import com.ifsc.contacerta.exception.ApiException;
import com.ifsc.contacerta.model.AccountStatus;
import com.ifsc.contacerta.model.MaterialKind;
import com.ifsc.contacerta.model.Role;
import com.ifsc.contacerta.repository.MaterialRepository;
import com.ifsc.contacerta.repository.UserRepository;
import com.ifsc.contacerta.storage.FileStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MaterialService {

	private final UserRepository userRepository;
	private final MaterialRepository materialRepository;
	private final FileStorage fileStorage;
	private final ExternalUrlValidator urlValidator;
	private final Clock clock;

	@Transactional
	public TeacherMaterialResponse create(UUID teacherId, CreateMaterialRequest request) {
		User teacher = requireActiveTeacher(teacherId);
		validateTarget(request);
		Instant now = Instant.now(clock);
		Material material;
		if (request.kind() == MaterialKind.FILE) {
			StoredFile file = requireOwnedFile(teacherId, request.fileId());
			material = Material.file(
					teacher, request.title().trim(), normalize(request.description()), normalize(request.category()), file, now
			);
		} else {
			String url = urlValidator.requireHttps(request.url(), "material");
			material = Material.externalLink(
					teacher, request.title().trim(), normalize(request.description()), normalize(request.category()), url, now
			);
		}
		return toResponse(materialRepository.save(material));
	}

	@Transactional
	public TeacherMaterialResponse update(UUID teacherId, UUID materialId, PatchMaterialRequest request) {
		requireActiveTeacher(teacherId);
		Material material = requireOwnedMaterial(teacherId, materialId);
		requireCurrentVersion(material, request.version());

		String title = request.title() == null ? material.getTitle() : request.title().trim();
		String description = resolveNullableText(request.description(), material.getDescription());
		String category = resolveNullableText(request.category(), material.getCategory());
		MaterialKind kind = request.kind() == null ? material.getKind() : request.kind();
		String url = resolveNullableText(request.url(), material.getExternalUrl());
		UUID fileId = resolveNullableUuid(request.fileId(), material.getFile());
		validateTarget(kind, url, fileId);

		if (kind == MaterialKind.FILE) {
			material.updateFile(title, description, category, requireOwnedFile(teacherId, fileId));
		} else {
			material.updateExternalLink(title, description, category, urlValidator.requireHttps(url, "material"));
		}
		return toResponse(material);
	}

	@Transactional(readOnly = true)
	public PageResponse<TeacherMaterialResponse> list(
			UUID teacherId, String search, MaterialKind kind, Pageable pageable
	) {
		requireActiveTeacher(teacherId);
		return PageResponse.from(materialRepository.searchOwned(
				teacherId, com.ifsc.contacerta.model.ContentStatus.ARCHIVED, normalize(search), kind, pageable
		).map(this::toResponse));
	}

	@Transactional(readOnly = true)
	public TeacherMaterialResponse get(UUID teacherId, UUID materialId) {
		requireActiveTeacher(teacherId);
		return toResponse(requireOwnedMaterial(teacherId, materialId));
	}

	@Transactional
	public void archive(UUID teacherId, UUID materialId) {
		requireActiveTeacher(teacherId);
		requireOwnedMaterial(teacherId, materialId).archive();
	}

	private void validateTarget(CreateMaterialRequest request) {
		validateTarget(request.kind(), request.url(), request.fileId());
	}

	private void validateTarget(MaterialKind kind, String url, UUID fileId) {
		boolean validFile = kind == MaterialKind.FILE && fileId != null && url == null;
		boolean validLink = kind == MaterialKind.EXTERNAL_LINK && fileId == null && url != null && !url.isBlank();
		if (!validFile && !validLink) {
			throw new ApiException(
					HttpStatus.UNPROCESSABLE_CONTENT, "INVALID_MEDIA", "Material kind and target are inconsistent."
			);
		}
	}

	private Material requireOwnedMaterial(UUID teacherId, UUID materialId) {
		return materialRepository.findByIdAndTeacherId(materialId, teacherId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MATERIAL_NOT_FOUND", "Material was not found."));
	}

	private void requireCurrentVersion(Material material, Long requestedVersion) {
		if (requestedVersion == null || requestedVersion != material.getVersion()) {
			throw new ApiException(
					HttpStatus.CONFLICT, "VERSION_CONFLICT", "The material was changed by another request."
			);
		}
	}

	private String resolveNullableText(JsonNode node, String current) {
		if (node == null) {
			return current;
		}
		return node.isNull() ? null : normalize(node.asText());
	}

	private UUID resolveNullableUuid(JsonNode node, StoredFile current) {
		if (node == null) {
			return current == null ? null : current.getId();
		}
		if (node.isNull()) {
			return null;
		}
		try {
			return UUID.fromString(node.asText());
		} catch (IllegalArgumentException exception) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "INVALID_MEDIA", "Material file ID is invalid.");
		}
	}

	private StoredFile requireOwnedFile(UUID teacherId, UUID fileId) {
		return fileStorage.findById(fileId)
				.filter(file -> file.getOwnerTeacher().getId().equals(teacherId))
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "FILE_NOT_FOUND", "File was not found."));
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

	private String normalize(String value) {
		if (value == null) {
			return null;
		}
		String normalized = value.trim();
		return normalized.isEmpty() ? null : normalized;
	}

	private TeacherMaterialResponse toResponse(Material material) {
		StoredFile stored = material.getFile();
		MaterialFileResponse file = stored == null ? null : new MaterialFileResponse(
				stored.getId(), stored.getFileName(), stored.getContentType(), stored.getSizeBytes()
		);
		return new TeacherMaterialResponse(
				material.getId(), material.getTitle(), material.getDescription(), material.getCategory(), material.getKind(),
				material.getExternalUrl(), file, material.getStatus(), material.getCreatedAt(), material.getUpdatedAt(),
				material.getVersion()
		);
	}
}
