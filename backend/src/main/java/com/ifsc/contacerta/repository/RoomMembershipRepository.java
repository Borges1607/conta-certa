package com.ifsc.contacerta.repository;

import com.ifsc.contacerta.entity.RoomMembership;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RoomMembershipRepository extends JpaRepository<RoomMembership, UUID> {

	Optional<RoomMembership> findByRoomIdAndStudentId(UUID roomId, UUID studentId);
}
