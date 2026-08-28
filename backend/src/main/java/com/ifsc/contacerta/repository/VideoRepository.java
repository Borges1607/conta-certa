package com.ifsc.contacerta.repository;

import com.ifsc.contacerta.entity.Video;
import com.ifsc.contacerta.model.ContentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface VideoRepository extends JpaRepository<Video, UUID> {
	Page<Video> findByTeacherIdAndStatusNot(UUID teacherId, ContentStatus status, Pageable pageable);
	Optional<Video> findByIdAndTeacherId(UUID id, UUID teacherId);
}
