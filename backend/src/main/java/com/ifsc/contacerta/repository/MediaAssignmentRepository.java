package com.ifsc.contacerta.repository;

import com.ifsc.contacerta.entity.MediaAssignment;
import com.ifsc.contacerta.model.MembershipStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MediaAssignmentRepository extends JpaRepository<MediaAssignment, UUID> {
	List<MediaAssignment> findByRoomIdOrderByPositionAsc(UUID roomId);
	Optional<MediaAssignment> findByIdAndRoomIdAndRoomTeacherId(UUID id, UUID roomId, UUID teacherId);
	boolean existsByRoomIdAndVideoId(UUID roomId, UUID videoId);
	boolean existsByRoomIdAndMaterialId(UUID roomId, UUID materialId);

	@Query("select assignment from MediaAssignment assignment "
			+ "join RoomMembership membership on membership.room.id = assignment.room.id "
			+ "where assignment.material.id = :materialId and membership.student.id = :studentId "
			+ "and membership.status = :status")
	List<MediaAssignment> findAccessibleMaterialAssignments(
			@Param("materialId") UUID materialId,
			@Param("studentId") UUID studentId,
			@Param("status") MembershipStatus status
	);

	@Query("select assignment from MediaAssignment assignment "
			+ "join RoomMembership membership on membership.room.id = assignment.room.id "
			+ "where assignment.video.id = :videoId and membership.student.id = :studentId "
			+ "and membership.status = :status")
	List<MediaAssignment> findAccessibleVideoAssignments(
			@Param("videoId") UUID videoId,
			@Param("studentId") UUID studentId,
			@Param("status") MembershipStatus status
	);
}
