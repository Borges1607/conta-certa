package com.ifsc.contacerta.repository;

import com.ifsc.contacerta.entity.Lesson;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LessonRepository extends JpaRepository<Lesson, UUID> {

	Optional<Lesson> findByIdAndTeacherId(UUID id, UUID teacherId);
}
