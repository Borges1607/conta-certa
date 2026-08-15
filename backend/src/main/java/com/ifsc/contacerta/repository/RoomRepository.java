package com.ifsc.contacerta.repository;

import com.ifsc.contacerta.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RoomRepository extends JpaRepository<Room, UUID> {

	Optional<Room> findByJoinCode(String joinCode);

	boolean existsByJoinCode(String joinCode);
}
