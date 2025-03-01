package com.dms.document.interaction.repository;

import com.dms.document.interaction.model.DocumentNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentNoteRepository extends JpaRepository<DocumentNote, Long> {
    Optional<DocumentNote> findByDocumentIdAndMentorId(String documentId, UUID mentorId);
    List<DocumentNote> findByDocumentId(String documentId);
    boolean existsByDocumentIdAndMentorId(String documentId, UUID mentorId);
}