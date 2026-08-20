package com.ifsc.contacerta.repository;

import com.ifsc.contacerta.entity.RoomMembership;
import com.ifsc.contacerta.dto.room.RoomStudentResponse;
import com.ifsc.contacerta.model.MembershipStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoomMembershipRepository extends JpaRepository<RoomMembership, UUID> {

	Optional<RoomMembership> findByRoomIdAndStudentId(UUID roomId, UUID studentId);

	List<RoomMembership> findByRoomIdAndStatusOrderByJoinedAtAsc(UUID roomId, MembershipStatus status);

	long countByRoomIdAndStatus(UUID roomId, MembershipStatus status);

	long countByRoomId(UUID roomId);

	@Query("""
			select new com.ifsc.contacerta.dto.room.RoomStudentResponse(
				membership.student.id,
				membership.student.fullName,
				membership.student.registrationNumber,
				membership.student.email,
				0,
				0,
				0,
				0,
				null,
				membership.status
			)
			from RoomMembership membership
			where membership.room.id = :roomId
			and membership.status = :status
			order by membership.joinedAt desc
			""")
	Page<RoomStudentResponse> findStudentResponsesByRoomIdAndStatusOrderByJoinedAtDesc(
			@Param("roomId") UUID roomId,
			@Param("status") MembershipStatus status,
			Pageable pageable
	);
}
