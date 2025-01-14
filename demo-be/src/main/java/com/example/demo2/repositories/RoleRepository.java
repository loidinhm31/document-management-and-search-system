package com.example.demo2.repositories;

import com.example.demo2.entities.Role;
import com.example.demo2.enums.AppRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<Role, UUID> {
    Optional<Role> findByRoleName(AppRole appRole);

}