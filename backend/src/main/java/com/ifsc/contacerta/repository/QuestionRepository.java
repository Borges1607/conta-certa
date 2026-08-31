package com.ifsc.contacerta.repository;

import com.ifsc.contacerta.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;
import java.util.List;
import java.util.Optional;

public interface QuestionRepository extends JpaRepository<Question, UUID> {

	long countByLessonIdAndActiveTrue(UUID lessonId);

	List<Question> findByLessonIdOrderByPositionAsc(UUID lessonId);

	List<Question> findByLessonIdAndActiveTrueOrderByPositionAsc(UUID lessonId);

	Optional<Question> findByIdAndLessonTeacherId(UUID id, UUID teacherId);

	@Query("select coalesce(max(question.position), 0) from Question question where question.lesson.id = :lessonId")
	int findMaximumPositionByLessonId(@Param("lessonId") UUID lessonId);
}
