package com.ifsc.contacerta.repository;

import com.ifsc.contacerta.dto.room.RoomStudentResponse;
import com.ifsc.contacerta.entity.RoomMembership;
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

	List<RoomMembership> findByStudentIdAndStatusOrderByJoinedAtDesc(UUID studentId, MembershipStatus status);

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
			and (
				:search is null or :search = ''
				or lower(membership.student.fullName) like lower(concat('%', :search, '%'))
				or lower(membership.student.registrationNumber) like lower(concat('%', :search, '%'))
				or lower(membership.student.email) like lower(concat('%', :search, '%'))
			)
			order by membership.joinedAt desc
			""")
	Page<RoomStudentResponse> findStudentResponsesByRoomIdAndStatusAndSearchOrderByJoinedAtDesc(
			@Param("roomId") UUID roomId,
			@Param("status") MembershipStatus status,
			@Param("search") String search,
			Pageable pageable
	);

	default Page<RoomStudentResponse> findStudentResponsesByRoomIdAndStatusOrderByJoinedAtDesc(
			UUID roomId,
			MembershipStatus status,
			Pageable pageable
	) {
		return findStudentResponsesByRoomIdAndStatusAndSearchOrderByJoinedAtDesc(roomId, status, null, pageable);
	}
}
