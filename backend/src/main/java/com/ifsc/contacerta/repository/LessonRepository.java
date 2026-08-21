package com.ifsc.contacerta.repository;

import com.ifsc.contacerta.entity.Lesson;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface LessonRepository extends JpaRepository<Lesson, UUID> {

	Optional<Lesson> findByIdAndTeacherId(UUID id, UUID teacherId);

	Page<Lesson> findByTeacherId(UUID teacherId, Pageable pageable);
}
