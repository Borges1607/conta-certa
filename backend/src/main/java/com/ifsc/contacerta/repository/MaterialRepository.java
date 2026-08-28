package com.ifsc.contacerta.repository;

import com.ifsc.contacerta.entity.Material;
import com.ifsc.contacerta.model.ContentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MaterialRepository extends JpaRepository<Material, UUID> {
	Page<Material> findByTeacherIdAndStatusNot(UUID teacherId, ContentStatus status, Pageable pageable);
	Optional<Material> findByIdAndTeacherId(UUID id, UUID teacherId);
}
