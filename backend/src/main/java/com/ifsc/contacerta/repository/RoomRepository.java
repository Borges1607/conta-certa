package com.ifsc.contacerta.repository;

import com.ifsc.contacerta.entity.Room;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface RoomRepository extends JpaRepository<Room, UUID>, JpaSpecificationExecutor<Room> {

	long countByTeacherId(UUID teacherId);

	long countByTeacherIdAndArchivedAtIsNull(UUID teacherId);

	long countByTeacherIdAndArchivedAtIsNotNull(UUID teacherId);

	Optional<Room> findByJoinCodeHash(String joinCodeHash);

	Optional<Room> findByJoinCodeHashAndInstitutionId(String joinCodeHash, UUID institutionId);

	Optional<Room> findByIdAndTeacherId(UUID id, UUID teacherId);

	boolean existsByJoinCodeHash(String joinCodeHash);

	boolean existsByTeacherIdAndName(UUID teacherId, String name);

	Page<Room> findByTeacherIdOrderByCreatedAtDesc(UUID teacherId, Pageable pageable);
}
