package com.ifsc.contacerta.repository;

import com.ifsc.contacerta.entity.RoomMembership;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface RankingRepository extends Repository<RoomMembership, UUID> {

	@Query(value = """
			with ranked as (
				select row_number() over (
						order by coalesce(progress.total_xp, 0) desc,
								 coalesce(progress.total_best_stars, 0) desc,
								 completion.first_completed_at asc nulls last,
								 student.id asc
					) as position,
					student.id as "studentId",
					student.full_name as "fullName",
					coalesce(progress.total_xp, 0) as "totalXp",
					coalesce(progress.total_best_stars, 0) as "totalStars",
					coalesce(progress.level, 1) as level
				from room_memberships membership
				join users student on student.id = membership.student_id
				left join room_student_progress progress
					on progress.room_id = membership.room_id
					and progress.student_id = membership.student_id
				left join lateral (
					select min(attempt.submitted_at) as first_completed_at
					from attempts attempt
					join lesson_assignments assignment on assignment.id = attempt.assignment_id
					where assignment.room_id = membership.room_id
						and attempt.student_id = membership.student_id
						and attempt.status in ('SUBMITTED', 'EXPIRED')
				) completion on true
				where membership.room_id = :roomId
					and membership.status = 'ACTIVE'
			)
			select position, "studentId", "fullName", "totalXp", "totalStars", level
			from ranked
			order by position
			""", countQuery = """
			select count(*)
			from room_memberships membership
			where membership.room_id = :roomId
				and membership.status = 'ACTIVE'
			""", nativeQuery = true)
	Page<RankingRowProjection> findPage(@Param("roomId") UUID roomId, Pageable pageable);

	@Query(value = """
			with ranked as (
				select row_number() over (
						order by coalesce(progress.total_xp, 0) desc,
								 coalesce(progress.total_best_stars, 0) desc,
								 completion.first_completed_at asc nulls last,
								 student.id asc
					) as position,
					student.id as "studentId",
					student.full_name as "fullName",
					coalesce(progress.total_xp, 0) as "totalXp",
					coalesce(progress.total_best_stars, 0) as "totalStars",
					coalesce(progress.level, 1) as level
				from room_memberships membership
				join users student on student.id = membership.student_id
				left join room_student_progress progress
					on progress.room_id = membership.room_id
					and progress.student_id = membership.student_id
				left join lateral (
					select min(attempt.submitted_at) as first_completed_at
					from attempts attempt
					join lesson_assignments assignment on assignment.id = attempt.assignment_id
					where assignment.room_id = membership.room_id
						and attempt.student_id = membership.student_id
						and attempt.status in ('SUBMITTED', 'EXPIRED')
				) completion on true
				where membership.room_id = :roomId
					and membership.status = 'ACTIVE'
			)
			select position, "studentId", "fullName", "totalXp", "totalStars", level
			from ranked
			where "studentId" = :studentId
			""", nativeQuery = true)
	Optional<RankingRowProjection> findStudent(
			@Param("roomId") UUID roomId,
			@Param("studentId") UUID studentId
	);
}
