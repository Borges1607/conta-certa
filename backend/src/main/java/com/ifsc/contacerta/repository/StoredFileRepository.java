package com.ifsc.contacerta.repository;

import com.ifsc.contacerta.entity.StoredFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface StoredFileRepository extends JpaRepository<StoredFile, UUID> {
	Optional<StoredFile> findByIdAndOwnerTeacherId(UUID id, UUID ownerTeacherId);

	@Query("""
			select file
			from StoredFile file
			where file.id = :fileId
			  and (
				file.ownerTeacher.id = :teacherId
				or exists (
					select material.id
					from Material material
					where material.file = file
					  and material.teacher.id = :teacherId
				)
			  )
			""")
	Optional<StoredFile> findDownloadableByTeacherId(
			@Param("fileId") UUID fileId,
			@Param("teacherId") UUID teacherId
	);

	@Query("""
			select file
			from StoredFile file
			where file.id = :fileId
			  and exists (
				select assignment.id
				from MediaAssignment assignment
				join assignment.material material
				join RoomMembership membership on membership.room = assignment.room
				where material.file = file
				  and material.kind = com.ifsc.contacerta.model.MaterialKind.FILE
				  and material.status = com.ifsc.contacerta.model.ContentStatus.PUBLISHED
				  and assignment.mediaType = com.ifsc.contacerta.model.MediaViewType.MATERIAL
				  and assignment.room.teacher = material.teacher
				  and membership.student.id = :studentId
				  and membership.status = com.ifsc.contacerta.model.MembershipStatus.ACTIVE
			  )
			""")
	Optional<StoredFile> findDownloadableByStudentId(
			@Param("fileId") UUID fileId,
			@Param("studentId") UUID studentId
	);
}
