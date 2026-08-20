package com.ifsc.contacerta.repository;

import com.ifsc.contacerta.entity.Room;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface RoomRepository extends JpaRepository<Room, UUID>, JpaSpecificationExecutor<Room> {

	Optional<Room> findByJoinCodeHash(String joinCodeHash);

	boolean existsByJoinCodeHash(String joinCodeHash);

	Page<Room> findByTeacherIdOrderByCreatedAtDesc(UUID teacherId, Pageable pageable);
}
