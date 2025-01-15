package com.sdms.authentication.repositories;

import com.sdms.authentication.entities.Role;
import com.sdms.authentication.enums.AppRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<Role, UUID> {
    Optional<Role> findByRoleName(AppRole appRole);

}