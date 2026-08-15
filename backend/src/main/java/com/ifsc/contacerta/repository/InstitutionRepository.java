package com.ifsc.contacerta.repository;

import com.ifsc.contacerta.entity.Institution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InstitutionRepository extends JpaRepository<Institution, UUID> {

	Optional<Institution> findByCnpj(String cnpj);

	List<Institution> findByActiveTrueOrderByNameAsc();
}
