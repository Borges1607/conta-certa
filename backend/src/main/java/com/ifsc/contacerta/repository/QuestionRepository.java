package com.ifsc.contacerta.repository;

import com.ifsc.contacerta.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface QuestionRepository extends JpaRepository<Question, UUID> {

	long countByLessonIdAndActiveTrue(UUID lessonId);
}
