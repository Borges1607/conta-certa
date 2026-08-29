package com.ifsc.contacerta.repository;

import com.ifsc.contacerta.entity.AchievementUnlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AchievementUnlockRepository extends JpaRepository<AchievementUnlock, UUID> {

	List<AchievementUnlock> findByRoomIdAndStudentId(UUID roomId, UUID studentId);

	@Modifying
	@Query(value = """
			insert into achievement_unlocks (id, room_id, student_id, achievement_code, unlocked_at)
			values (:id, :roomId, :studentId, :code, :unlockedAt)
			on conflict (room_id, student_id, achievement_code) do nothing
			""", nativeQuery = true)
	int insertIfAbsent(
			@Param("id") UUID id,
			@Param("roomId") UUID roomId,
			@Param("studentId") UUID studentId,
			@Param("code") String code,
			@Param("unlockedAt") Instant unlockedAt
	);
}
