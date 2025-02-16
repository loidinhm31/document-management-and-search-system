package com.dms.document.interaction.repository;

import com.dms.document.interaction.model.DocumentFavorite;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentFavoriteRepository extends JpaRepository<DocumentFavorite, Long> {
    boolean existsByUserIdAndDocumentId(UUID userId, String documentId);
    void deleteByUserIdAndDocumentId(UUID userId, String documentId);
    Page<DocumentFavorite> findByUserId(UUID userId, Pageable pageable);
    List<DocumentFavorite> findByDocumentId(String documentId);
}