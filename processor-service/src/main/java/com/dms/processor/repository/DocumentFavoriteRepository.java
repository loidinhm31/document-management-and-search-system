package com.dms.processor.repository;

import com.dms.processor.model.DocumentFavorite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Set;
import java.util.UUID;

public interface DocumentFavoriteRepository extends JpaRepository<DocumentFavorite, Long> {
    @Query("SELECT df.userId FROM DocumentFavorite df WHERE df.documentId = :documentId")
    Set<UUID> findUserIdsByDocumentId(@Param("documentId") String documentId);
}
