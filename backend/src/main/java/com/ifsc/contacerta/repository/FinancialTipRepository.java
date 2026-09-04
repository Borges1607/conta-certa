package com.ifsc.contacerta.repository;

import com.ifsc.contacerta.entity.FinancialTip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface FinancialTipRepository extends JpaRepository<FinancialTip, UUID>, JpaSpecificationExecutor<FinancialTip> {
	Optional<FinancialTip> findByIdAndArchivedAtIsNull(UUID id);
}
