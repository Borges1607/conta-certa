package com.ifsc.contacerta.repository;

import com.ifsc.contacerta.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface RoomRepository extends JpaRepository<Room, UUID> {

	@Query("select room from Room room where upper(room.joinCode) = upper(:joinCode)")
	Optional<Room> findByJoinCode(@Param("joinCode") String joinCode);

	boolean existsByJoinCode(String joinCode);

	Page<Room> findByTeacherIdOrderByCreatedAtDesc(UUID teacherId, Pageable pageable);
}
