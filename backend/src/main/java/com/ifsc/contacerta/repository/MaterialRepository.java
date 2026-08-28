package com.ifsc.contacerta.repository;

import com.ifsc.contacerta.entity.Material;
import com.ifsc.contacerta.model.ContentStatus;
import com.ifsc.contacerta.model.MaterialKind;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface MaterialRepository extends JpaRepository<Material, UUID> {
	Page<Material> findByTeacherIdAndStatusNot(UUID teacherId, ContentStatus status, Pageable pageable);

	@Query("""
			select material from Material material
			where material.teacher.id = :teacherId
			and material.status <> :excludedStatus
			and (:search is null or lower(material.title) like lower(concat('%', :search, '%'))
				or lower(coalesce(material.description, '')) like lower(concat('%', :search, '%')))
			and (:kind is null or material.kind = :kind)
			""")
	Page<Material> searchOwned(
			@Param("teacherId") UUID teacherId,
			@Param("excludedStatus") ContentStatus excludedStatus,
			@Param("search") String search,
			@Param("kind") MaterialKind kind,
			Pageable pageable
	);

	Optional<Material> findByIdAndTeacherId(UUID id, UUID teacherId);
}
