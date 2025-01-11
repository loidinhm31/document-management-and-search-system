package com.example.demo2.repositories;

import com.example.demo2.enums.AppRole;
import com.example.demo2.models.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findByRoleName(AppRole appRole);

}