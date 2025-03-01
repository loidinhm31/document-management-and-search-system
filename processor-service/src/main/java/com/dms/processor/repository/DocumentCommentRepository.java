package com.dms.processor.repository;

import com.dms.processor.model.DocumentComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DocumentCommentRepository extends JpaRepository<DocumentComment, Long> {
    Optional<DocumentComment> findByDocumentIdAndId(String documentId, Long id);

}