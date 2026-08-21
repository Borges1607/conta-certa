package com.ifsc.contacerta.repository;

import com.ifsc.contacerta.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.List;

public interface QuestionRepository extends JpaRepository<Question, UUID> {

	long countByLessonIdAndActiveTrue(UUID lessonId);

	List<Question> findByLessonIdOrderByPositionAsc(UUID lessonId);
}
