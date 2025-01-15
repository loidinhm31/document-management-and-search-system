package com.sdms.auth.repositories;

import com.sdms.auth.entities.Role;
import com.sdms.auth.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);

    @Query("SELECT COUNT(u) FROM User u WHERE u.enabled = true")
    long countByEnabledTrue();

    @Query("SELECT COUNT(u) FROM User u WHERE u.accountNonLocked = false")
    long countByAccountNonLockedFalse();

    @Query("SELECT COUNT(u) FROM User u WHERE u.accountNonExpired = false")
    long countByAccountNonExpiredFalse();

    @Query("SELECT COUNT(u) FROM User u WHERE u.role = :role")
    long countByRole(Role role);
}