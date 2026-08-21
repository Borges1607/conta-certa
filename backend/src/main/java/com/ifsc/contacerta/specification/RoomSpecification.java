package com.ifsc.contacerta.specification;

import com.ifsc.contacerta.entity.Room;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;

public final class RoomSpecification {

	private RoomSpecification() {
	}

	public static Specification<Room> ownedBy(UUID teacherId, String search, Boolean archived) {
		return (root, query, criteriaBuilder) -> {
			var predicates = new ArrayList<Predicate>();
			predicates.add(criteriaBuilder.equal(root.get("teacher").get("id"), teacherId));

			if (search != null && !search.isBlank()) {
				String normalizedSearch = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
				predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), normalizedSearch));
			}
			if (archived != null) {
				predicates.add(archived
						? criteriaBuilder.isNotNull(root.get("archivedAt"))
						: criteriaBuilder.isNull(root.get("archivedAt")));
			}

			return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
		};
	}
}
