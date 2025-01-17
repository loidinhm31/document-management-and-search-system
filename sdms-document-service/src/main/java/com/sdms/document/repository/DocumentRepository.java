package com.sdms.document.repository;

import com.sdms.document.entity.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {
    Optional<Document> findByIdAndUser_Username(UUID id, String username);

    Optional<Document> findByIdAndUser_UserId(UUID id, UUID userId);

    Page<Document> findByUser_UsernameAndDeletedFalse(String username, Pageable pageable);

    @Query("SELECT d FROM Document d WHERE d.user.username = :username AND d.deleted = false " +
            "AND (LOWER(d.originalFilename) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "OR LOWER(d.contentType) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Document> searchUserDocuments(@Param("username") String username,
                                       @Param("search") String search,
                                       Pageable pageable);
}