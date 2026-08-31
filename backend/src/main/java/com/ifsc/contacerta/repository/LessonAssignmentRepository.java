package com.ifsc.contacerta.repository;

import com.ifsc.contacerta.entity.LessonAssignment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LessonAssignmentRepository extends JpaRepository<LessonAssignment, UUID> {

	List<LessonAssignment> findByRoomIdAndRoomTeacherIdOrderByPositionAsc(UUID roomId, UUID teacherId);

	Optional<LessonAssignment> findByIdAndRoomIdAndRoomTeacherId(UUID id, UUID roomId, UUID teacherId);

	boolean existsByRoomIdAndLessonId(UUID roomId, UUID lessonId);

	boolean existsByLessonId(UUID lessonId);

	List<LessonAssignment> findByRoomIdAndStatusOrderByPositionAsc(UUID roomId, com.ifsc.contacerta.model.ContentStatus status);

	Optional<LessonAssignment> findByRoomIdAndLessonId(UUID roomId, UUID lessonId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select assignment from LessonAssignment assignment where assignment.room.id = :roomId order by assignment.position")
	List<LessonAssignment> findByRoomIdForUpdate(@Param("roomId") UUID roomId);
}
