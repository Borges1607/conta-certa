package com.ifsc.contacerta.repository;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class AdminHistoryQueryRepository {

	private final EntityManager entityManager;

	public boolean hasInstitutionHistory(UUID institutionId) {
		return exists("""
				select exists (
					select 1 from users where institution_id = :id
					union all select 1 from rooms where institution_id = :id
				)
				""", institutionId);
	}

	public boolean hasTeacherHistory(UUID teacherId) {
		return exists("""
				select exists (
					select 1 from rooms where teacher_id = :id
					union all select 1 from lessons where teacher_id = :id
					union all select 1 from videos where teacher_id = :id
					union all select 1 from materials where teacher_id = :id
					union all select 1 from extra_attempt_grants where teacher_id = :id
				)
				""", teacherId);
	}

	private boolean exists(String sql, UUID id) {
		return (Boolean) entityManager.createNativeQuery(sql)
				.setParameter("id", id)
				.getSingleResult();
	}
}
