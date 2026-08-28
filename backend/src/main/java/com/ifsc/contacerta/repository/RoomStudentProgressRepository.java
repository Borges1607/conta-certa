package com.ifsc.contacerta.repository;

import com.ifsc.contacerta.entity.RoomStudentProgress;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;
import java.util.UUID;

public interface RoomStudentProgressRepository extends JpaRepository<RoomStudentProgress, UUID> {
	Optional<RoomStudentProgress> findByRoomIdAndStudentId(UUID roomId, UUID studentId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<RoomStudentProgress> findForUpdateByRoomIdAndStudentId(UUID roomId, UUID studentId);
}
