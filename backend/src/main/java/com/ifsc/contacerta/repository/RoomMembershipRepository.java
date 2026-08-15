package com.ifsc.contacerta.repository;

import com.ifsc.contacerta.entity.RoomMembership;
import com.ifsc.contacerta.model.MembershipStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoomMembershipRepository extends JpaRepository<RoomMembership, UUID> {

	Optional<RoomMembership> findByRoomIdAndStudentId(UUID roomId, UUID studentId);

	List<RoomMembership> findByRoomIdAndStatusOrderByJoinedAtAsc(UUID roomId, MembershipStatus status);
}
