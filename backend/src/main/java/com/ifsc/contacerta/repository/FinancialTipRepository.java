package com.ifsc.contacerta.repository;

import com.ifsc.contacerta.entity.FinancialTip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FinancialTipRepository extends JpaRepository<FinancialTip, UUID>, JpaSpecificationExecutor<FinancialTip> {
	Optional<FinancialTip> findByIdAndArchivedAtIsNull(UUID id);

	List<FinancialTip> findByActiveTrueAndArchivedAtIsNullAndPublicationDateOrderByIdAsc(LocalDate publicationDate);

	List<FinancialTip> findByActiveTrueAndArchivedAtIsNullOrderByIdAsc();
}
