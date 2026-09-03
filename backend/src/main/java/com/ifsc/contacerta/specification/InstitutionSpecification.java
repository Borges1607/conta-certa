package com.ifsc.contacerta.specification;

import com.ifsc.contacerta.entity.Institution;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.Locale;

public final class InstitutionSpecification {

	private InstitutionSpecification() {
	}

	public static Specification<Institution> filtered(String search, Boolean active) {
		return (root, query, builder) -> {
			var predicates = new ArrayList<Predicate>();
			if (search != null && !search.isBlank()) {
				String normalized = search.trim().toLowerCase(Locale.ROOT);
				String digits = search.replaceAll("\\D", "");
				Predicate name = builder.like(builder.lower(root.get("name")), "%" + normalized + "%");
				Predicate cnpj = digits.isBlank()
						? builder.disjunction()
						: builder.like(root.get("cnpj"), "%" + digits + "%");
				predicates.add(builder.or(name, cnpj));
			}
			if (active != null) {
				predicates.add(builder.equal(root.get("active"), active));
			}
			return builder.and(predicates.toArray(Predicate[]::new));
		};
	}
}
