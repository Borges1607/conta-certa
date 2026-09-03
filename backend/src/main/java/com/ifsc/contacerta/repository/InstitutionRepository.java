package com.ifsc.contacerta.repository;

import com.ifsc.contacerta.entity.Institution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InstitutionRepository extends JpaRepository<Institution, UUID>, JpaSpecificationExecutor<Institution> {

	Optional<Institution> findByCnpj(String cnpj);

	List<Institution> findByActiveTrueOrderByNameAsc();
}
