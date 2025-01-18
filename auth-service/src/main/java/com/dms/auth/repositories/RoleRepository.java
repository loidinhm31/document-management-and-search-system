package com.dms.auth.repositories;

import com.dms.auth.entities.Role;
import com.dms.auth.enums.AppRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<Role, UUID> {
    Optional<Role> findByRoleName(AppRole appRole);

}