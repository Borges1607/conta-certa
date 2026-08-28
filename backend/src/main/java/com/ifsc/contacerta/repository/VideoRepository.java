package com.ifsc.contacerta.repository;

import com.ifsc.contacerta.entity.Video;
import com.ifsc.contacerta.model.ContentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface VideoRepository extends JpaRepository<Video, UUID> {
	Page<Video> findByTeacherIdAndStatusNot(UUID teacherId, ContentStatus status, Pageable pageable);

	@Query("""
			select video from Video video
			where video.teacher.id = :teacherId
			and video.status <> :excludedStatus
			and (:search is null or lower(video.title) like lower(concat('%', :search, '%'))
				or lower(coalesce(video.description, '')) like lower(concat('%', :search, '%')))
			and (:category is null or video.category = :category)
			""")
	Page<Video> searchOwned(
			@Param("teacherId") UUID teacherId,
			@Param("excludedStatus") ContentStatus excludedStatus,
			@Param("search") String search,
			@Param("category") String category,
			Pageable pageable
	);

	Optional<Video> findByIdAndTeacherId(UUID id, UUID teacherId);
}
