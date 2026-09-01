package com.ifsc.contacerta.repository;

import com.ifsc.contacerta.entity.LessonAssignment;
import com.ifsc.contacerta.model.ContentStatus;
import com.ifsc.contacerta.model.MembershipStatus;
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

	Optional<LessonAssignment> findByIdAndRoomTeacherId(UUID id, UUID teacherId);

	boolean existsByRoomIdAndLessonId(UUID roomId, UUID lessonId);

	boolean existsByLessonId(UUID lessonId);

	List<LessonAssignment> findByRoomIdAndStatusOrderByPositionAsc(UUID roomId, ContentStatus status);

	@Query("""
			select assignment from LessonAssignment assignment
			join RoomMembership membership on membership.room = assignment.room
			where assignment.room.id = :roomId
			and membership.student.id = :studentId
			and membership.status = :membershipStatus
			and assignment.status = :assignmentStatus
			order by assignment.position
			""")
	List<LessonAssignment> findAccessibleByRoomIdAndStudentIdAndStatusOrderByPositionAsc(
			@Param("roomId") UUID roomId,
			@Param("studentId") UUID studentId,
			@Param("membershipStatus") MembershipStatus membershipStatus,
			@Param("assignmentStatus") ContentStatus assignmentStatus
	);

	@Query("""
			select assignment from LessonAssignment assignment
			join RoomMembership membership on membership.room = assignment.room
			where assignment.room.id = :roomId
			and assignment.lesson.id = :lessonId
			and membership.student.id = :studentId
			and membership.status = :membershipStatus
			""")
	Optional<LessonAssignment> findAccessibleByRoomIdAndLessonIdAndStudentId(
			@Param("roomId") UUID roomId,
			@Param("lessonId") UUID lessonId,
			@Param("studentId") UUID studentId,
			@Param("membershipStatus") MembershipStatus membershipStatus
	);

	@Query("""
			select assignment from LessonAssignment assignment
			join RoomMembership membership on membership.room = assignment.room
			where assignment.id = :assignmentId
			and membership.student.id = :studentId
			and membership.status = :membershipStatus
			""")
	Optional<LessonAssignment> findAccessibleByIdAndStudentId(
			@Param("assignmentId") UUID assignmentId,
			@Param("studentId") UUID studentId,
			@Param("membershipStatus") MembershipStatus membershipStatus
	);

	Optional<LessonAssignment> findByRoomIdAndLessonId(UUID roomId, UUID lessonId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select assignment from LessonAssignment assignment "
			+ "where assignment.room.id = :roomId and assignment.room.teacher.id = :teacherId "
			+ "order by assignment.position")
	List<LessonAssignment> findByRoomIdAndRoomTeacherIdForUpdate(
			@Param("roomId") UUID roomId,
			@Param("teacherId") UUID teacherId
	);
}
