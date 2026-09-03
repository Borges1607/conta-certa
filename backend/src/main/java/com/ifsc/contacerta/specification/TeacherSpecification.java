package com.ifsc.contacerta.specification;

import com.ifsc.contacerta.entity.User;
import com.ifsc.contacerta.model.AccountStatus;
import com.ifsc.contacerta.model.Role;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;

public final class TeacherSpecification {

	private TeacherSpecification() {
	}

	public static Specification<User> filtered(String search, AccountStatus status, UUID institutionId) {
		return (root, query, builder) -> {
			var predicates = new ArrayList<Predicate>();
			predicates.add(builder.equal(root.get("role"), Role.TEACHER));
			if (search != null && !search.isBlank()) {
				String value = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
				predicates.add(builder.or(
						builder.like(builder.lower(root.get("fullName")), value),
						builder.like(builder.lower(root.get("email")), value),
						builder.like(builder.lower(root.get("registrationNumber")), value)
				));
			}
			if (status != null) {
				predicates.add(builder.equal(root.get("status"), status));
			}
			if (institutionId != null) {
				predicates.add(builder.equal(root.get("institution").get("id"), institutionId));
			}
			return builder.and(predicates.toArray(Predicate[]::new));
		};
	}
}
