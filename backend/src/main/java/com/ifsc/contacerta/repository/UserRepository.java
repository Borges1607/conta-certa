package com.ifsc.contacerta.repository;

import com.ifsc.contacerta.entity.User;
import com.ifsc.contacerta.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

	Optional<User> findByEmailIgnoreCase(String email);

	boolean existsByEmailIgnoreCase(String email);

	long countByInstitutionIdAndRole(UUID institutionId, Role role);

	Optional<User> findByIdAndRole(UUID id, Role role);

	long countByRole(Role role);

	long countByRoleAndStatus(Role role, com.ifsc.contacerta.model.AccountStatus status);
}
