package com.sdms.auth.repositories;

import com.sdms.auth.entities.Role;
import com.sdms.auth.enums.AppRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<Role, UUID> {
    Optional<Role> findByRoleName(AppRole appRole);

}