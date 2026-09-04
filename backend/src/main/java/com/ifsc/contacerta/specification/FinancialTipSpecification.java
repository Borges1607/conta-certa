package com.ifsc.contacerta.specification;

import com.ifsc.contacerta.entity.FinancialTip;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Locale;

public final class FinancialTipSpecification {
	private FinancialTipSpecification() {
	}

	public static Specification<FinancialTip> filtered(String search, Boolean active, LocalDate publicationDate) {
		return (root, query, builder) -> {
			var predicates = new ArrayList<Predicate>();
			predicates.add(builder.isNull(root.get("archivedAt")));
			if (search != null && !search.isBlank()) {
				predicates.add(builder.like(builder.lower(root.get("title")), "%" + search.trim().toLowerCase(Locale.ROOT) + "%"));
			}
			if (active != null) {
				predicates.add(builder.equal(root.get("active"), active));
			}
			if (publicationDate != null) {
				predicates.add(builder.equal(root.get("publicationDate"), publicationDate));
			}
			return builder.and(predicates.toArray(Predicate[]::new));
		};
	}
}
