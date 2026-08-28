package com.ifsc.contacerta.repository;

import com.ifsc.contacerta.entity.MediaView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;
import java.util.Optional;

public interface MediaViewRepository extends JpaRepository<MediaView, UUID> {
	Optional<MediaView> findByStudentIdAndRoomIdAndVideoId(UUID studentId, UUID roomId, UUID videoId);
	Optional<MediaView> findByStudentIdAndRoomIdAndMaterialId(UUID studentId, UUID roomId, UUID materialId);

	@Modifying
	@Query(value = """
			insert into media_views
			(id, student_id, room_id, media_type, video_id, material_id, first_viewed_at, last_viewed_at, view_count)
			values (:id, :studentId, :roomId, 'VIDEO', :videoId, null, :viewedAt, :viewedAt, 1)
			on conflict (student_id, room_id, video_id) where video_id is not null do update
			set last_viewed_at = excluded.last_viewed_at, view_count = media_views.view_count + 1
			""", nativeQuery = true)
	void upsertVideo(
			@Param("id") UUID id,
			@Param("studentId") UUID studentId,
			@Param("roomId") UUID roomId,
			@Param("videoId") UUID videoId,
			@Param("viewedAt") Instant viewedAt
	);

	@Modifying
	@Query(value = """
			insert into media_views
			(id, student_id, room_id, media_type, video_id, material_id, first_viewed_at, last_viewed_at, view_count)
			values (:id, :studentId, :roomId, 'MATERIAL', null, :materialId, :viewedAt, :viewedAt, 1)
			on conflict (student_id, room_id, material_id) where material_id is not null do update
			set last_viewed_at = excluded.last_viewed_at, view_count = media_views.view_count + 1
			""", nativeQuery = true)
	void upsertMaterial(
			@Param("id") UUID id,
			@Param("studentId") UUID studentId,
			@Param("roomId") UUID roomId,
			@Param("materialId") UUID materialId,
			@Param("viewedAt") Instant viewedAt
	);
}
