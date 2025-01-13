package com.example.demo2.repositories;

import com.example.demo2.models.Role;
import com.example.demo2.models.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {
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

    @Query("SELECT u FROM User u " +
            "WHERE (:search IS NULL OR " +
            "LOWER(u.username) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%'))) " +
            "AND (:enabled IS NULL OR u.enabled = :enabled) " +
            "AND (:roleId IS NULL OR u.role.roleId = :roleId)")
    Page<User> findBySearchCriteria(String search, Boolean enabled, Long roleId, Pageable pageable);
}